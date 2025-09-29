package com.mongodb.solcon.tests;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.mongodb.solcon.BaseMongoTest;
import com.mongodb.solcon.DocumentFactory;
import com.mongodb.solcon.Utils;
import java.util.*;
import org.apache.commons.lang3.RandomUtils;
import org.bson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* In this test we want ot be able to test retrieving by an index and perhaps retrieving not by an index */

public class ConcurrencyTest extends BaseMongoTest {
  private static final Logger logger = LoggerFactory.getLogger(ConcurrencyTest.class);

  final int SAMPLESTRINGLENGTH = 1024 * 1024;
  MongoDatabase database;
  MongoCollection<RawBsonDocument> collection;
  int maxFieldsPerObject;
  String bigrandomString;
  DocumentFactory docFactory = null;

  int docsizeBytes = 2048;

  public ConcurrencyTest(MongoClient client, Document config, long nThreads, long threadNo) {
    super(client, config, nThreads, threadNo);

    database = mongoClient.getDatabase(testConfig.getString("database"));
    collection = database.getCollection(testConfig.getString("collection"), RawBsonDocument.class);
    parseTestParams();

    bigrandomString = Utils.BigRandomText(SAMPLESTRINGLENGTH);
    maxFieldsPerObject =
        Objects.requireNonNullElse(testConfig.getInteger("maxFieldsPerObject"), 200);

    // Approx Doc Size in bytes - get from top level unless in varaint
    if (testConfig.getDouble("docSizeKB") != null) {
      docsizeBytes = (int) (testConfig.getDouble("docSizeKB") * 1024);
    }

    docFactory = new DocumentFactory(threadNo, "OBJECTID", docsizeBytes, maxFieldsPerObject);
  }

  void parseTestParams() {}

  public void run() {
    int nHotSpots = testConfig.getInteger("nHotSpots", 1);
    int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 100000);
    int[] hotSpotArray = new int[nHotSpots];
    // Calcualte the hotspots
    for (int i = 0; i < nHotSpots; i++) {
      hotSpotArray[i] = (int) (initialDocsToInsert * (i / (double) nHotSpots));
    }
    Document variant = testConfig.get("variant", Document.class);
    int nUpdates = variant.getInteger("nUpdates", 100000);
    int nUpdatesPerThread = Math.toIntExact(nUpdates / nThreads);
    if (threadNo == 0) {
      logger.info("Updates Per Thread = {}, Threads = {} ", nUpdatesPerThread, nThreads);
    }
    for (int i = 0; i < nUpdatesPerThread; i++) {
      int hotSpotId = hotSpotArray[RandomUtils.nextInt(0, nHotSpots)];
      collection.updateOne(Filters.eq("_id", hotSpotId), Updates.inc("count", 1));
    }
  }

  // Reset is called for each variant

  public void GenerateData() {
    parseTestParams();

    int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 100000);

    if (collection.estimatedDocumentCount() == initialDocsToInsert) {
      logger.info(
          "Collection already contains {} documents - not regenerating", initialDocsToInsert);
    } else {
      if (initialDocsToInsert > 0) {
        collection.drop();

        List<RawBsonDocument> batch = new ArrayList<>();
        int size = 0;
        int reportCount = 0;
        for (int doc = 0; doc < initialDocsToInsert; doc++) {
          Document extraFields = new Document("_id", doc);
          extraFields.put("count", 0);

          RawBsonDocument d = docFactory.createDocument(extraFields);
          // Add some fields at the end to Query By
          //
          batch.add(d);
          reportCount++;
          size = size + d.getByteBuffer().remaining();
          int writeBatchSize = 1000;
          if (batch.size() >= writeBatchSize) {
            try {
              collection.insertMany(batch);
            } catch (Exception e) {
              logger.error("Error inserting batch", e);
            }

            if (reportCount > initialDocsToInsert / 20) {
              logger.info(
                  "Data Generation: Generated {} of {} = {}% complete",
                  doc + 1, initialDocsToInsert, (double) (((doc + 1) * 100) / initialDocsToInsert));
              reportCount = 0;
            }
            batch.clear();
          }
        }
        if (!batch.isEmpty()) {
          if (threadNo == 0) logger.info("Generated final batch of {}", (batch.size()));

          try {
            collection.insertMany(batch);
          } catch (Exception e) {
            logger.error("Error inserting batch", e);
          }
          batch.clear();
        }
      }
    }
  }

  public void TestReset() {
    parseTestParams();
    if (testConfig.containsKey("variant")) {
      Document variant = testConfig.get("variant", Document.class);
      if (variant.getBoolean("indexUpdate", false)) {
        logger.info("Creating index _id, count");
        collection.createIndex(Indexes.ascending("_id", "count"));
      } else {
        logger.info("Dropping index _id, count if exists");
        try {
          collection.dropIndex(Indexes.ascending("_id", "count"));
        } catch (Exception e) {
          logger.debug("Error dropping index", e);
        }
      }
    }
  }

  // WarmCache is called before each rune
  public void WarmCache() {
    logger.info("No cache warm up was required");
  }

  // TODO - Figure out what wa want to control in our document
  // Size, NFields and Depth I guess
  // We want ot be able to generate data very fast but pseudo random
  private Document createDocument() {

    return new Document();
  }
}
