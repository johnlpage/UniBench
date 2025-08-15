package com.mongodb.solcon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtlasClusterManager {
  private static final Logger logger = LoggerFactory.getLogger(AtlasClusterManager.class);
  private final String baseUrl = "https://cloud.mongodb.com/api/atlas/v1.0";
  private final String publicKey;
  private final String privateKey;
  private final String projectId;
  private final CloseableHttpClient httpClient;

  public AtlasClusterManager() {
    this.publicKey = System.getenv("ATLAS_PUBLIC_KEY");
    this.privateKey = System.getenv("ATLAS_PRIVATE_KEY");
    this.projectId = System.getenv("ATLAS_PROJECT_ID");
    if (this.publicKey == null || this.privateKey == null || this.projectId == null) {
      logger.warn("AtlasClusterManager requires configuration");
      System.exit(1);
    }
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(
        AuthScope.ANY, new UsernamePasswordCredentials(publicKey, privateKey));
    this.httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
  }

  public void createCluster(String clusterName, String tier) throws Exception {
    int isClusterReady = isClusterReady(clusterName);

    if (isClusterReady == 200) {
      logger.info("Cluster already exists and is ready: " + clusterName);
      return;
    }
    // It does not exist
    if (isClusterReady == 404) {
      String url = baseUrl + "/groups/" + projectId + "/clusters";
      String payload =
          String.format(
              """
    {
      "name": "%s",
      "clusterType": "REPLICASET",
      "providerSettings": {
        "providerName": "AWS",
        "regionName": "EU_WEST_1",
        "instanceSizeName": "%s",
        "diskSizeGB": 60
      },
      "autoScaling": {
        "diskGBEnabled": false,
        "compute": { "enabled": false }
      }
    }
    """,
              clusterName, tier);

      HttpPost request = new HttpPost(url);
      request.setHeader("Content-Type", "application/json");
      request.setEntity(new StringEntity(payload));

      HttpResponse response = httpClient.execute(request);
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= 300) {
        throw new IOException("Failed to create cluster: " + response.getStatusLine());
      }
      logger.info("Cluster creation initiated for: " + clusterName);
    }

    blockUntilClusterReady(clusterName, true);
  }

  public int isClusterReady(String clusterName) throws Exception {
    String url = baseUrl + "/groups/" + projectId + "/clusters/" + clusterName;

    HttpGet request = new HttpGet(url);
    HttpResponse response = httpClient.execute(request);
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == 404) {
      return 404;
    } // Does not exist
    if (statusCode >= 300)
      throw new IOException("Error getting cluster status: " + response.getStatusLine());

    String body = EntityUtils.toString(response.getEntity());
    if (body.contains("\"stateName\":\"IDLE\"")) {
      return 200;
    } // It's up
    return 201; // It's been created already: 0)
  }

  public void blockUntilClusterReady(String clusterName, boolean state) throws Exception {
    logger.info(
        "Polling cluster status for: "
            + clusterName
            + "BUG TOFIX: If this is the last line output and nothing is "
            + "following run again");
    int tries = 0;
    while (tries < 100) {
      tries++;
      int clusterReady = isClusterReady(clusterName);
      if (state && clusterReady == 200) {
        logger.info("Cluster is up and ready: " + clusterName);
        return;
      }
      if (!state && clusterReady == 404) {
        logger.info("Cluster is now down: " + clusterName);
        return;
      }
      logger.info("Waiting for cluster to be " + (state ? "ready" : "deleted") + "(" + tries + ")");
      TimeUnit.SECONDS.sleep(30);
    }
    throw new RuntimeException("Timeout waiting for cluster to be ready.");
  }

  public void deleteCluster(String clusterName) throws Exception {
    String url = baseUrl + "/groups/" + projectId + "/clusters/" + clusterName;
    HttpDelete request = new HttpDelete(url);
    HttpResponse response = httpClient.execute(request);
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == 404) {
      logger.info("Cluster not found: " + clusterName);
      return;
    }
    if (statusCode >= 300) {
      throw new IOException("Failed to delete cluster: " + response.getStatusLine());
    }
    logger.info("Cluster deletion initiated for: " + clusterName);
    blockUntilClusterReady(clusterName, false);
  }

  // TODO - Use this as required, fr example to verify is cluster is correct instance type
  String getClusterDetails(String clusterName) throws Exception {
    String url = baseUrl + "/groups/" + projectId + "/clusters/" + clusterName;
    HttpGet request = new HttpGet(url);
    HttpResponse response = httpClient.execute(request);
    int statusCode = response.getStatusLine().getStatusCode();

    if (statusCode == 200) {
      String body = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = new ObjectMapper();
      JsonNode json = mapper.readTree(body);
      String connectionString = json.get("mongoURIWithSRV").asText(); // or "mongoURI"
      return connectionString;
    } else {
      throw new RuntimeException("Could not get cluster info: " + response.getStatusLine());
    }
  }
}
