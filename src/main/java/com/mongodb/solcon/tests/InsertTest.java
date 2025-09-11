package com.mongodb.solcon.tests;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.solcon.BaseMongoTest;
import com.mongodb.solcon.Utils;
import java.util.*;
import org.bson.*;
import org.bson.BsonBinary;
import org.bson.BsonBinarySubType;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* This tests measures inserting data into a MognoDB cluster, It supports the following test specific parameters.
These may be defined at the top level or in the variant array
    * totalDocsToInsert - How much data to insert
    * docSizeKB ( in KB )
    * maxFieldsPerObject (TODO)
    * numindexes (TODO)
 */
public class InsertTest extends BaseMongoTest {
  private static final Logger logger = LoggerFactory.getLogger(InsertTest.class);
  final int MEANSTRINGLENGTH = 32;
  final int SAMPLESTRINGLENGTH = 1024 * 1024;
  MongoDatabase database;
  MongoCollection<RawBsonDocument> collection;
  MongoCollection<RawBsonDocument> initialCollection;
  int maxFieldsPerObject;
  int docsizeBytes = 2048;
  String bigrandomString;
  int totalDocsToInsert;
  int writeBatchSize;
  int nSecondaryIndexes;
  String idType;

  HashMap<Integer, Integer> map = new HashMap<>();

  /* A FieldSet is a combination of an Integer, a Date and a String - The strings can very in length uniformly*/
  /* We use this set of 3 fields repeated in our documents */

  public InsertTest(MongoClient client, Document config, long nThreads, long threadNo) {
    super(client, config, nThreads, threadNo);

    database = mongoClient.getDatabase(testConfig.getString("database"));
    collection = database.getCollection(testConfig.getString("collection"), RawBsonDocument.class);
    initialCollection =
        database.getCollection(
            testConfig.getString("collection") + "_initial", RawBsonDocument.class);
    parseTestParams();

    bigrandomString = Utils.BigRandomText(SAMPLESTRINGLENGTH);
    maxFieldsPerObject =
        Objects.requireNonNullElse(testConfig.getInteger("maxFieldsPerObject"), 200);
  }

  private static byte[] asBytes(UUID uuid) {
    long mostSignificantBits = uuid.getMostSignificantBits();
    long leastSignificantBits = uuid.getLeastSignificantBits();
    byte[] bytes = new byte[16];

    for (int i = 0; i < 8; i++) {
      bytes[i] = (byte) (mostSignificantBits >>> (8 * (7 - i)));
      bytes[8 + i] = (byte) (leastSignificantBits >>> (8 * (7 - i)));
    }
    return bytes;
  }

  void parseTestParams() {
    Document variant = testConfig.get("variant", Document.class);

    idType = testConfig.getString("idType");
    if (variant != null && variant.getString("idType") != null) {
      idType = variant.getString("idType");
    }

    // Allow variation of batch size

    writeBatchSize = testConfig.getInteger("writeBatchSize", 1000);
    if (variant != null && variant.getInteger("writeBatchSize") != null) {
      writeBatchSize = variant.getInteger("writeBatchSize");
    }

    nSecondaryIndexes = testConfig.getInteger("nSecondaryIndexes", 0);
    if (variant != null && variant.getInteger("nSecondaryIndexes") != null) {
      nSecondaryIndexes = variant.getInteger("nSecondaryIndexes");
    }

    // Approx Doc Size in bytes - get from top level unless in varaint
    if (testConfig.getDouble("docSizeKB") != null) {
      docsizeBytes = (int) (testConfig.getDouble("docSizeKB") * 1024);
    }

    totalDocsToInsert = testConfig.getInteger("totalDocsToInsert", 1000);

    if (variant != null && variant.getDouble("docSizeKB") != null) {
      docsizeBytes = (int) (variant.getDouble("docSizeKB") * 1024);
    }

    if (variant != null && variant.getInteger("totalDocsToInsert") != null) {
      totalDocsToInsert = variant.getInteger("totalDocsToInsert");
    }
  }

  public void run() {

    // Try to have defaults

    int docsPerThread = (int) (totalDocsToInsert / nThreads);
    List<RawBsonDocument> batch = new ArrayList<>();
    int size = 0;
    int reportCount = 0;
    for (int doc = 0; doc < docsPerThread; doc++) {
      RawBsonDocument d = createDocument();
      batch.add(d);
      reportCount++;
      size = size + d.getByteBuffer().remaining();
      if (batch.size() >= writeBatchSize) {
        try {
          collection.insertMany(batch);
        } catch (Exception e) {
          logger.error("Error inserting batch", e);
        }

        if (threadNo == 0 && reportCount > docsPerThread / 20) {
          logger.info(
              "Inserted {} of {} = {}% complete",
              (doc + 1) * nThreads,
              docsPerThread * nThreads,
              (double) (((doc + 1) * 100) / docsPerThread));
          reportCount = 0;
        }
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      if (threadNo == 0) logger.info("Inserting final batch of {}", (batch.size()));

      try {
        collection.insertMany(batch);
      } catch (Exception e) {
        logger.error("Error inserting batch", e);
      }
      batch.clear();
    }
  }

  // Reset is called for each variant

  public void GenerateData() {
    parseTestParams();
    // If we have "initialDocsToInsert" then generate that many in a collection
    // During reset() we will copy that into the test table - this is meaningful
    // When talking about indexes. Where an empty index is much less expensive
    int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 0);
    if (initialDocsToInsert > 0) {
      initialCollection.drop();
      List<RawBsonDocument> batch = new ArrayList<>();
      int size = 0;
      int reportCount = 0;
      for (int doc = 0; doc < initialDocsToInsert; doc++) {
        RawBsonDocument d = createDocument();
        batch.add(d);
        reportCount++;
        size = size + d.getByteBuffer().remaining();
        if (batch.size() >= writeBatchSize) {
          try {
            initialCollection.insertMany(batch);
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
          initialCollection.insertMany(batch);
        } catch (Exception e) {
          logger.error("Error inserting batch", e);
        }
        batch.clear();
      }
    }
  }

  public void TestReset() {
    parseTestParams();

    logger.info("Dropping {}", collection.getNamespace());
    collection.drop();
    // If we need any secondary indices, make them here
    for (int idxno = 0; idxno < nSecondaryIndexes; idxno++) {

      logger.info("Creating secondary index on {}", "intfield" + (idxno + 1));
      collection.createIndex(new Document("intfield" + (idxno + 1), 1));
    }

    int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 0);
    if (initialDocsToInsert > 0) {
      logger.info(
          "Copying {} documents from initial collection to test collection",
          initialCollection.estimatedDocumentCount());
      initialCollection
          .aggregate(
              Arrays.asList(new Document("$out", collection.getNamespace().getCollectionName())))
          .first();
      logger.info(
          "Copying complete:{} docs in test collection", collection.estimatedDocumentCount());
    }
  }

  // WarmCache is called before each rune
  public void WarmCache() {
    logger.info("No cache warm up was required");
  }

  // TODO - Figure out what wa want to control in our document
  // Size, NFields and Depth I guess
  // We want ot be able to generate data very fast but pseudo random
  private RawBsonDocument createDocument() {
    BasicOutputBuffer buffer = new BasicOutputBuffer();
    BsonWriter writer = new BsonBinaryWriter(buffer);
    int fNo = 1;

    writer.writeStartDocument();
    if (idType != null) {

      switch (idType) {
        case "UUID":
          UUID uuid = UUID.randomUUID();
          BsonBinary bsonBinary = new BsonBinary(BsonBinarySubType.UUID_STANDARD, asBytes(uuid));
          writer.writeBinaryData("_id", bsonBinary);
          break;
        case "BUSINESS_ID":
          int cust = random.nextInt(20000);
          int custOneUp = map.getOrDefault(cust, 0);
          map.put(cust, custOneUp + 1);
          String busId = String.format("ACC%05dx_%06x%02x", cust, custOneUp, threadNo);
          writer.writeString("_id", busId);
          break;
        default:
          writer.writeObjectId("_id", new ObjectId());
          break;
      }
    }
    while (buffer.size() < docsizeBytes) {

      writer.writeInt32("intfield" + fNo, random.nextInt(100_000));
      writer.writeDateTime(
          ("datefield" + fNo), 1754917200011L + random.nextLong(10_000_000_000L)); // TODO Bound
      // these to
      // sensible values
      int stringLength = random.nextInt(MEANSTRINGLENGTH) + 1;
      int offset = random.nextInt(SAMPLESTRINGLENGTH - MEANSTRINGLENGTH);
      String example = bigrandomString.substring(offset, offset + stringLength);
      writer.writeString("stringfield" + fNo, example);
      fNo++;
    }
    writer.writeEndDocument();

    return new RawBsonDocument(buffer.toByteArray());
  }
}
