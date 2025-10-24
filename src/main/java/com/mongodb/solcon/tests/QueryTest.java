package com.mongodb.solcon.tests;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.solcon.BaseMongoTest;
import com.mongodb.solcon.DocumentFactory;
import com.mongodb.solcon.Utils;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.bson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* In this test we want ot be able to test retrieving by an index and perhaps retrieving not by an index */

public class QueryTest extends BaseMongoTest {
  private static final Pattern RAND_INT = Pattern.compile("^RANDINT\\((\\d+),(\\d+)\\)$");
  private static final Pattern RAND_INT_LIST =
      Pattern.compile("^RANDINTLIST\\((\\d+),(\\d+),(\\d+)\\)$");
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

    Document variant = testConfig.get("variant", Document.class);
    int nQueries = variant.getInteger("nQueries", 100000);
    int nQueriesPerThread = Math.toIntExact(nQueries / nThreads);

    int limit = variant.getInteger("limit", 1);
    int skip = variant.getInteger("skip", 0);
    Document projection = variant.get("projection", Document.class);
    if (projection != null) {
      projection = new Document("_id", 0).append("nofieldsplease", 1);
    }
    Document queryTemplate = variant.get("query", Document.class);
    Document statusBefore =
        mongoClient.getDatabase("admin").runCommand(new Document("serverStatus", 1));

    Document newQuery = processDocument(queryTemplate);
    if (threadNo == 0) {
      logger.info("Example: {}", newQuery.toJson());
      Document explain =
          collection
              .find(newQuery)
              .limit(limit)
              .skip(skip)
              .projection(projection)
              .explain(ExplainVerbosity.EXECUTION_STATS);
      logger.info("Explain: {}", explain.toJson());
    }
    // Let's grab an explain too

    for (int i = 0; i < nQueriesPerThread; i++) {
      newQuery = processDocument(queryTemplate);

      AtomicInteger totalLength = new AtomicInteger();
      AtomicInteger count = new AtomicInteger();

      collection
          .find(newQuery)
          .limit(limit)
          .skip(skip)
          .projection(projection)
          .forEach(
              item -> {
                count.incrementAndGet();
                totalLength.addAndGet(item.getByteBuffer().limit()); // length in character
              });

      if (count.get() != limit) {
        logger.error(
            "Count {}  was not equal to limit {}: {}", count.get(), limit, newQuery.toJson());
        System.exit(1);
      }
    }

    if (threadNo == 0) {
      Document statusAfter =
          mongoClient.getDatabase("admin").runCommand(new Document("serverStatus", 1));
      showCacheRead(statusBefore, statusAfter);
    }
  }

  private void showCacheRead(Document statusBefore, Document statusAfter) {
    long cb;
    long ca;
    try {
      // Work round the fact this type changes!
      cb =
          (long)
              statusBefore
                  .get("wiredTiger", new Document())
                  .get("cache", new Document())
                  .getInteger("bytes read into cache");
    } catch (Exception e) {
      cb =
          statusBefore
              .get("wiredTiger", new Document())
              .get("cache", new Document())
              .getLong("bytes read into cache");
    }

    try {
      ca =
          (long)
              statusAfter
                  .get("wiredTiger", new Document())
                  .get("cache", new Document())
                  .getInteger("bytes read into cache");
    } catch (Exception e) {
      ca =
          statusAfter
              .get("wiredTiger", new Document())
              .get("cache", new Document())
              .getLong("bytes read into cache");
    }

    logger.info(
        "{} MB of data was read into the cache during this test.", (ca - cb) / (1024 * 1024));
  }

  // Reset is called for each variant

  public void GenerateData() {
    parseTestParams();

    int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 100000);
    int groupSize = testConfig.getInteger("groupSize", 400);

    if (collection.estimatedDocumentCount() == initialDocsToInsert) {
      logger.info(
          "Collection already contains {} documents - not regenerating", initialDocsToInsert);
    } else {
      if (initialDocsToInsert > 0) {
        collection.drop();

        collection.createIndex(new Document("group", 1)); // Groups of N

        collection.createIndex(
            new Document("group", 1).append("group_seq_i", 1)); // Position in Group
        Document extraFields = new Document();
        List<RawBsonDocument> batch = new ArrayList<>();
        int size = 0;
        int reportCount = 0;
        int nGroups = initialDocsToInsert / groupSize;
        for (int doc = 0; doc < initialDocsToInsert; doc++) {
          // Serial Numbering
          extraFields.put("_id", doc);
          // A Group of related data is not co-located usually
          extraFields.put("group", doc % nGroups);
          // A Group of related data is not co-located usually
          extraFields.put("group_seq", Math.floor(doc / nGroups));
          extraFields.put("group_seq_i", Math.floor(doc / nGroups));
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

  @SuppressWarnings("unchecked")
  private Document processDocument(Document inputDoc) {
    Document processedDoc = new Document();
    for (Map.Entry<String, Object> entry : inputDoc.entrySet()) {
      processedDoc.put(entry.getKey(), processValue(entry.getValue()));
    }
    return processedDoc;
  }

  /**
   * Recursively process a value. If it's a string with RANDINT or RANDINTLIST, replace it with
   * actual random data. Supports nested documents and lists.
   */
  @SuppressWarnings("unchecked")
  private Object processValue(Object input) {
    if (input instanceof String) {
      String str = (String) input;

      var m = RAND_INT.matcher(str);
      if (m.matches()) {
        int from = Integer.parseInt(m.group(1));
        int to = Integer.parseInt(m.group(2));
        return random.nextInt((to - from) + 1) + from - 1;
      }

      m = RAND_INT_LIST.matcher(str);
      if (m.matches()) {
        int from = Integer.parseInt(m.group(1));
        int to = Integer.parseInt(m.group(2));
        int len = Integer.parseInt(m.group(3));

        // Ensure we don’t ask for more unique numbers than possible
        if (len > (to - from + 1)) {
          throw new IllegalArgumentException(
              "Not enough unique numbers in range to satisfy length " + len);
        }

        Set<Integer> valuesSet = new LinkedHashSet<>();
        while (valuesSet.size() < len) {
          valuesSet.add(random.nextInt((to - from) + 1) + from - 1);
        }

        return new ArrayList<>(valuesSet);
      }

      return str;
    } else if (input instanceof Document) {
      Document originalDoc = (Document) input;
      Document processedDoc = new Document();
      for (Map.Entry<String, Object> entry : originalDoc.entrySet()) {
        processedDoc.put(entry.getKey(), processValue(entry.getValue()));
      }
      return processedDoc;
    } else if (input instanceof List<?>) {
      List<?> originalList = (List<?>) input;
      List<Object> processedList = new ArrayList<>();
      for (Object elem : originalList) {
        processedList.add(processValue(elem));
      }
      return processedList;
    } else {
      return input; // leave unchanged
    }
  }
}
