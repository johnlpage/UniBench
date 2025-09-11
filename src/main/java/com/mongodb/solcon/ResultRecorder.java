package com.mongodb.solcon;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.TreeMap;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* For now, just supporting what we need - write results to Atlas for later analysis */

public class ResultRecorder {
  private static final Logger logger = LoggerFactory.getLogger(ResultRecorder.class);
  final String databaseName = "unibench";
  final String collectionName = "results";
  final String historyCollectionName = "results_history";
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

  private static Document safeGet(Document doc, String key) {
    return doc != null ? (Document) doc.get(key) : null;
  }

  // Very Generic
  protected void recordResult(
      Document benchConfig,
      Document testConfig,
      Document variant,
      Document beforeStatus,
      Document afterStatus,
      Document metrics,
      Instant startTime,
      Instant endTime) {

    if (!enabled) {
      return;
    }
    Document testRunInfo = new Document();
    testRunInfo.put("start_time", Date.from(startTime));
    testRunInfo.put("end_time", Date.from(endTime));
    testRunInfo.put("duration", Duration.between(startTime, endTime).toMillis());
    testRunInfo.put("variant", variant);
    testRunInfo.put("test_config", testConfig);
    testRunInfo.put("before_status", SanitiseStats(beforeStatus));
    testRunInfo.put("after_status", SanitiseStats(afterStatus));
    testRunInfo.put("metrics", metrics);
    testRunInfo.put("bench_config", benchConfig);

    MongoCollection<Document> collection =
        mongoClient.getDatabase(databaseName).getCollection(collectionName);
    MongoCollection<Document> historyCollection =
        mongoClient.getDatabase(databaseName).getCollection(historyCollectionName);

    Document id = Document.parse(variant.toJson()); // Deep copy
    id.put("testname", testConfig.getString("filename"));
    id = new Document(new TreeMap<>(id)); // Sort the fields
    historyCollection.insertOne(testRunInfo);
    testRunInfo.remove("_id");
    ReplaceOptions options = new ReplaceOptions().upsert(true);
    collection.replaceOne(new Document("_id", id), testRunInfo, options);
  }

  // $where breaks flex and free tier
  Document SanitiseStats(Document stats) {
    // Usage Example
    Document metrics = safeGet(stats, "metrics");
    Document operatorCounters = safeGet(metrics, "operatorCounters");
    Document match = safeGet(operatorCounters, "match");
    if (match != null) {
      match.remove("$where");
    }

    Document expressions = safeGet(operatorCounters, "expressions");
    if (expressions != null) {
      expressions.remove("$function");
    }

    Document groupAccumulators = safeGet(operatorCounters, "groupAccumulators");
    if (groupAccumulators != null) {
      groupAccumulators.remove("$accumulator");
    }
    return stats;
  }
}
