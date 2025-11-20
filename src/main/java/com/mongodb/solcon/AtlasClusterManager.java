package com.mongodb.solcon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtlasClusterManager {
  private static final Logger logger = LoggerFactory.getLogger(AtlasClusterManager.class);
  private final String baseUrl = "https://cloud.mongodb.com/api/atlas/v2";
  private final String projectId;
  private final CloseableHttpClient httpClient;

  public AtlasClusterManager() {
    String publicKey = System.getenv("ATLAS_PUBLIC_KEY");
    String privateKey = System.getenv("ATLAS_PRIVATE_KEY");
    this.projectId = System.getenv("ATLAS_PROJECT_ID");
    if (publicKey == null || privateKey == null || this.projectId == null) {
      logger.warn("AtlasClusterManager requires configuration");
      System.exit(1);
    }
    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    credsProvider.setCredentials(
        AuthScope.ANY, new UsernamePasswordCredentials(publicKey, privateKey));
    this.httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
  }

  public void modifyCluster(
      String clusterName, String tier, String diskType, int diskSize, int iops) throws Exception {
    int isClusterReady = isClusterReady(clusterName);

    if (isClusterReady == 404) {
      logger.info(
          "Cluster does not exist so cannot be modified - creating it instead " + clusterName);
      callClusterAPI(clusterName, tier, diskType, diskSize, iops, false);
    }
    callClusterAPI(clusterName, tier, diskType, diskSize, iops, true);
  }

  public void createCluster(
      String clusterName, String tier, String diskType, int diskSize, int iops) throws Exception {
    int isClusterReady = isClusterReady(clusterName);

    if (isClusterReady == 200) {
      logger.info("Cluster already exists and is ready: " + clusterName);
      return;
    }
    callClusterAPI(clusterName, tier, diskType, diskSize, iops, false);
  }

  public void callClusterAPI(
      String clusterName, String tier, String diskType, int diskSize, int iops, boolean modify)
      throws Exception {
    int isClusterReady = isClusterReady(clusterName);

    String url = baseUrl + "/groups/" + projectId + "/clusters";
    logger.info(
        "Launching Cluster {} : {} with {}GB {} disks ({} IOPS)",
        clusterName,
        tier,
        diskSize,
        diskType,
        iops);

    String payload =
        String.format(
            """
    {
      "name": "%s",
      "backupEnabled": false,
                                    "biConnector": {
                                      "enabled": false,
                                      "readPreference": "secondary"
                                    },
                                    "clusterType": "REPLICASET",
   "diskSizeGB": %d,
   "diskWarmingMode": "FULLY_WARMED",
   "encryptionAtRestProvider": "NONE",
   "globalClusterSelfManagedSharding": false,
    "mongoDBMajorVersion": "8.0",
    "mongoDBVersion": "8.0.13",
    "pitEnabled": false,
        "replicationSpecs": [
          {
            "numShards": 1,
            "regionConfigs": [
              {
                "electableSpecs": {
                  "diskIOPS": %d,
                  "ebsVolumeType": "%s",
                  "instanceSize": "%s",
                  "nodeCount": 3
                },
                "priority": 7,
                "providerName": "AWS",
                "regionName": "EU_WEST_1",
                "analyticsAutoScaling": {
                  "compute": {
                    "enabled": false,
                    "scaleDownEnabled": false
                  },
                  "diskGB": {
                    "enabled": false
                  }
                },
                "analyticsSpecs": {
                  "nodeCount": 0,
                  "diskIOPS": 3000,
                  "ebsVolumeType": "STANDARD",
                  "instanceSize": "M30"
                },
                "autoScaling": {
                  "compute": {
                    "enabled": false,
                    "scaleDownEnabled": false
                  },
                  "diskGB": {
                    "enabled": false
                  }
                },
                "readOnlySpecs": {
                  "nodeCount": 0,
                  "diskIOPS": 3000,
                  "ebsVolumeType": "STANDARD",
                  "instanceSize": "%s"
                }
              }
            ],
            "zoneName": "Zone 1"
          }
          ]
    }
    """,
            clusterName, diskSize, iops, diskType, tier, tier);
    logger.debug(payload);
    // It does not exist we need to create it
    if (isClusterReady == 404) {
      HttpPost request = new HttpPost(url);
      request.setHeader("Content-Type", "application/json");
      request.setHeader(HttpHeaders.ACCEPT, "application/vnd.atlas.2023-11-15+json");
      request.setEntity(new StringEntity(payload));

      HttpResponse response = httpClient.execute(request);
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= 300) {
        logger.error("Failed to create cluster: " + response.getStatusLine());
        logger.error(payload);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          String responseBody = EntityUtils.toString(entity); // Consume the entity
          System.out.println(responseBody);
        }
        throw new IOException("Failed to create cluster: " + response.getStatusLine());
      }

      logger.info("Cluster creation initiated for: " + clusterName);

      try {
        // Process the response entity
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          String responseBody = EntityUtils.toString(entity); // Consume the entity
          System.out.println(responseBody);

          // Send a Patch to fix the Oplog size
          String fixOplog =
              """
                    { "oplogSizeMB": 12345  }
                    """;
          HttpPatch patchRequest = new HttpPatch(url + "/" + clusterName + "/processArgs");
          patchRequest.setHeader("Content-Type", "application/json");
          patchRequest.setHeader(HttpHeaders.ACCEPT, "application/vnd.atlas.2023-11-15+json");
          patchRequest.setEntity(new StringEntity(fixOplog));
          HttpResponse patchResponse = httpClient.execute(patchRequest);
          int patchStatusCode = patchResponse.getStatusLine().getStatusCode();
          if (patchStatusCode >= 300) {
            logger.error("Failed to fix oplog size: " + patchResponse.getStatusLine());
          } else {
            entity = patchResponse.getEntity();
            if (entity != null) {
              responseBody = EntityUtils.toString(entity); // Consume the entity
              System.out.println(responseBody);
            }
          }
        }
      } finally {
        // Close the response
        EntityUtils.consume(response.getEntity()); // Ensures the connection can be reused
      }
    } else if (modify) {
      HttpPatch request = new HttpPatch(url + "/" + clusterName);
      request.setHeader("Content-Type", "application/json");
      request.setHeader(HttpHeaders.ACCEPT, "application/vnd.atlas.2023-11-15+json");
      request.setEntity(new StringEntity(payload));

      HttpResponse response = httpClient.execute(request);
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= 300) {
        logger.error("Failed to modify cluster: " + response.getStatusLine());
        logger.error(payload);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          String responseBody = EntityUtils.toString(entity); // Consume the entity
          System.out.println(responseBody);
        }
        throw new IOException("Failed to modify cluster: " + response.getStatusLine());
      }
      logger.info("Cluster modification initiated for: " + clusterName);

      try {
        // Process the response entity
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          String responseBody = EntityUtils.toString(entity); // Consume the entity
          System.out.println(responseBody);
        }
      } finally {
        // Close the response
        EntityUtils.consume(response.getEntity()); // Ensures the connection can be reused
      }
    }

    blockUntilClusterReady(clusterName, true);
  }

  public int isClusterReady(String clusterName) throws Exception {
    String url = baseUrl + "/groups/" + projectId + "/clusters/" + clusterName;
    int rval = -1;
    HttpGet request = new HttpGet(url);
    request.setHeader(HttpHeaders.ACCEPT, "application/vnd.atlas.2023-11-15+json");
    HttpResponse response = httpClient.execute(request);
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == 404) {
      EntityUtils.consume(response.getEntity());
      return 404;
    } // Does not exist
    if (statusCode >= 300)
      throw new IOException("Error getting cluster status: " + response.getStatusLine());

    String body = EntityUtils.toString(response.getEntity());
    EntityUtils.consume(response.getEntity());
    if (body.contains("\"stateName\":\"IDLE\"")) {
      return 200;
    } // It's up
    EntityUtils.consume(response.getEntity());
    return 201; // It's been created already: 0)
  }

  public void blockUntilClusterReady(String clusterName, boolean state) throws Exception {

    int tries = 0;
    boolean first = true;
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
      logger.info(
          "Waiting for cluster to be {} (check {}) - current state:{}",
          (state ? "ready" : "deleted"),
          tries,
          clusterReady);
      if (first) {
        TimeUnit.SECONDS.sleep(10);
        first = false;
      } else {
        TimeUnit.SECONDS.sleep(30);
      }
    }
    throw new RuntimeException("Timeout waiting for cluster to be ready.");
  }

  public void deleteCluster(String clusterName) throws Exception {
    String url = baseUrl + "/groups/" + projectId + "/clusters/" + clusterName;
    HttpDelete request = new HttpDelete(url);
    request.setHeader(HttpHeaders.ACCEPT, "application/vnd.atlas.2023-11-15+json");
    HttpResponse response = httpClient.execute(request);
    int statusCode = response.getStatusLine().getStatusCode();
    EntityUtils.consume(response.getEntity());
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

  public Document getClusterPrimaryMetrics(String clusterName, String startTime, String endTime)
      throws Exception {
    // Step 1: Get all processes for the project
    String processesUrl = String.format("%s/groups/%s/processes", baseUrl, projectId);

    HttpGet processesRequest = new HttpGet(processesUrl);
    processesRequest.setHeader(HttpHeaders.ACCEPT, "application/vnd.atlas.2023-11-15+json");
    processesRequest.setHeader("Content-Type", "application/json");
    processesRequest.setHeader(HttpHeaders.ACCEPT, "application/vnd.atlas.2023-11-15+json");
    HttpResponse processesResponse = httpClient.execute(processesRequest);
    int processesStatusCode = processesResponse.getStatusLine().getStatusCode();

    if (processesStatusCode != 200) {
      String processesResponseBody = EntityUtils.toString(processesResponse.getEntity());
      logger.error(processesResponseBody);
      throw new IOException("Failed to retrieve processes: " + processesResponse.getStatusLine());
    }

    String processesResponseBody = EntityUtils.toString(processesResponse.getEntity());
    ObjectMapper processesMapper = new ObjectMapper();
    JsonNode processesNode = processesMapper.readTree(processesResponseBody);

    // Locate the process ID for the primary node in the specified cluster
    String primaryProcessId = null;
    for (JsonNode processNode : processesNode.path("results")) {
      if (processNode.path("typeName").asText().equals("REPLICA_PRIMARY")
          && processNode.path("userAlias").asText().startsWith(clusterName.toLowerCase())) {
        primaryProcessId = processNode.path("id").asText();
        break;
      }
    }

    if (primaryProcessId == null) {
      throw new IllegalStateException("Primary process ID not found for cluster: " + clusterName);
    }

    // Step 2: Retrieve measurements for the primary process ID
    String measurementsUrl =
        String.format(
            "%s/groups/%s/processes/%s/measurements", baseUrl, projectId, primaryProcessId);
    String queryParams = String.format("?granularity=PT1M&start=%s&end=%s", startTime, endTime);

    HttpGet measurementsRequest = new HttpGet(measurementsUrl + queryParams);
    measurementsRequest.setHeader(HttpHeaders.ACCEPT, "application/vnd.atlas.2023-11-15+json");
    measurementsRequest.setHeader("Content-Type", "application/json");

    HttpResponse measurementsResponse = httpClient.execute(measurementsRequest);
    int measurementsStatusCode = measurementsResponse.getStatusLine().getStatusCode();

    if (measurementsStatusCode != 200) {
      String measurementsResponseBody = EntityUtils.toString(measurementsResponse.getEntity());
      logger.error(measurementsResponseBody);
      throw new IOException(
          "Failed to retrieve measurements: " + measurementsResponse.getStatusLine());
    }

    String measurementsResponseBody = EntityUtils.toString(measurementsResponse.getEntity());

    /* I'm not convinced measuring the IOPS used it the most useful thing in general, we want to
    know How much we write out (which in general is sequential) How much we read into cache (how many blocks) as that's generally random
     When we write from cache we write Collection and Oplog, we also need to account for journal   writes] This i may be in staus */

    // Get Disk Information
    String dataDiskMeasurements =
        String.format(
            "%s/groups/%s/processes/%s/disks/data/measurements",
            baseUrl, projectId, primaryProcessId);

    HttpGet disksRequest = new HttpGet(dataDiskMeasurements + queryParams);
    disksRequest.setHeader("Content-Type", "application/json");
    disksRequest.setHeader(HttpHeaders.ACCEPT, "application/vnd.atlas.2023-11-15+json");
    HttpResponse disksResponse = httpClient.execute(disksRequest);
    int disksStatusCode = disksResponse.getStatusLine().getStatusCode();
    String disksResponseBody = EntityUtils.toString(disksResponse.getEntity());
    if (disksStatusCode != 200) {

      logger.error(disksResponseBody);
      throw new IOException("Failed to retrieve list of disks: " + disksResponse.getStatusLine());
    }

    Document diskMetrics = Document.parse(disksResponseBody);

    Document rval = Document.parse(measurementsResponseBody);
    rval.put("diskMetrics", diskMetrics.get("measurements"));
    return rval;
  }
}
