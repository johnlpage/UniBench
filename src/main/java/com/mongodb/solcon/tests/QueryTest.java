package com.mongodb.solcon.tests;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.solcon.BaseMongoTest;
import com.mongodb.solcon.DocumentFactory;
import com.mongodb.solcon.Utils;
import java.util.*;
import org.bson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* In this test we want ot be able to test retrieving by an index and perhaps retrieving not by an index */

public class QueryTest extends BaseMongoTest {
  private static final Logger logger = LoggerFactory.getLogger(QueryTest.class);
  final int MEANSTRINGLENGTH = 32;
  final int SAMPLESTRINGLENGTH = 1024 * 1024;
  MongoDatabase database;
  MongoCollection<RawBsonDocument> collection;
  int maxFieldsPerObject;
  String bigrandomString;
  DocumentFactory docFactory = null;
  HashMap<Integer, Integer> map = new HashMap<>();
  int docsizeBytes = 2048;

  public QueryTest(MongoClient client, Document config, long nThreads, long threadNo) {
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
    var rangeMatcher = java.util.regex.Pattern.compile("^RANDINT\\((\\d+),(\\d+)\\)");
    Document variant = testConfig.get("variant", Document.class);
    int nQueries = variant.getInteger("nQueries", 100000);
    int nQueriesPerThread = Math.toIntExact(nQueries / nThreads);

    int limit = variant.getInteger("limit", 1);
    Document projection = variant.get("projection", Document.class);
    if (projection != null) {
      projection = new Document("_id", 0).append("nofieldsplease", 1);
    }
    Document queryTemplate = variant.get("query", Document.class);
    for (int i = 0; i < nQueriesPerThread; i++) {
      Document newQuery = new Document();
      for (var field : queryTemplate.entrySet()) {
        String fname = field.getKey().toString();
        String fvalue = field.getValue().toString();

        var m = rangeMatcher.matcher(fvalue);
        if (m.matches()) {
          int from = Integer.parseInt(m.group(1));
          int to = Integer.parseInt(m.group(2));
          int value = random.nextInt(from, to);
          newQuery.put(fname, value);
        }
      }
      collection.find(newQuery).limit(limit).projection(projection).forEach(item -> {});
    }
  }

  // Reset is called for each variant

  public void GenerateData() {
    parseTestParams();

    int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 100000);
    int nGroups = testConfig.getInteger("nGroups", 100);
    int groupSize = initialDocsToInsert / nGroups;

    if (collection.estimatedDocumentCount() == initialDocsToInsert) {
      logger.info(
          "Collection already contains {} documents - not regenerating", initialDocsToInsert);
    } else {
      if (initialDocsToInsert > 0) {
        collection.drop();
        collection.createIndex(new Document("seq", 1));
        collection.createIndex(new Document("seq_group", 1));
        collection.createIndex(new Document("seq_group_mod", 1));
        Document extraFields = new Document();
        List<RawBsonDocument> batch = new ArrayList<>();
        int size = 0;
        int reportCount = 0;
        for (int doc = 0; doc < initialDocsToInsert; doc++) {
          // Serial Numbering
          extraFields.put("seq", doc);
          extraFields.put("seq_group", Math.floorDiv(doc, groupSize));
          extraFields.put("seq_group_mod", doc % groupSize);

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
