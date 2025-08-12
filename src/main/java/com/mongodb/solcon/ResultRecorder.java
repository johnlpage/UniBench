package com.mongodb.solcon;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import java.util.Date;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* For now, just supporting what we need - write results to Atlas for later analysis */

public class ResultRecorder {
  private static final Logger logger = LoggerFactory.getLogger(ResultRecorder.class);
  final String databaseName = "unibench";
  final String collectionName = "results";
  MongoClient mongoClient;
  boolean enabled = false;

  ResultRecorder() {
    String mongoURI = System.getenv("MONGO_RECORDING_URI");
    if (mongoURI == null) {
      logger.warn("MONGO_RECORDING_URI not defined: NOT LOGGING RESULTS");
      return;
    }
    try {
      logger.info("Connecting to Atlas cluster to record results");
      mongoClient = MongoClients.create(mongoURI);

      Document rval = mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
      enabled = true;
    } catch (Exception e) {
      logger.error("An error occurred while connecting to MongoDB", e);
      System.exit(1);
    }
  }

  // Very Generic
  protected void recordResult(
      Document benchConfig,
      Document testConfig,
      String mode,
      Document beforeStatus,
      Document afterStatus,
      Date startTime,
      Date endTime) {

    if (!enabled) {
      return;
    }
    Document testRunInfo = new Document();
    testRunInfo.put("start_time", startTime);
    testRunInfo.put("end_time", endTime);
    testRunInfo.put("mode", mode);
    testRunInfo.put("test_config", testConfig);
    testRunInfo.put("before_status", SanitiseStats(beforeStatus));
    testRunInfo.put("after_status", SanitiseStats(afterStatus));
    testRunInfo.put("bench_config", benchConfig);

    MongoCollection<Document> collection =
        mongoClient.getDatabase(databaseName).getCollection(collectionName);
    collection.insertOne(testRunInfo);
  }

  // $where breaks flex and free tier
  Document SanitiseStats(Document stats) {
    ((Document) ((Document) ((Document) stats.get("metrics")).get("operatorCounters")).get("match"))
        .remove("$where");
    ((Document)
            ((Document) ((Document) stats.get("metrics")).get("operatorCounters"))
                .get("expressions"))
        .remove("$function");

    ((Document)
            ((Document) ((Document) stats.get("metrics")).get("operatorCounters"))
                .get("groupAccumulators"))
        .remove("$accumulator");
    return stats;
  }
}
