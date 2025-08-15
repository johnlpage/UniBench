package com.mongodb.solcon.tests;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.solcon.BaseMongoTest;
import com.mongodb.solcon.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bson.*;
import org.bson.io.BasicOutputBuffer;
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
  int maxFieldsPerObject = 200;
  int docsizeBytes = 2048;
  String bigrandomString;
  int totalDocsToInsert;
  int writeBatchSize = 1000;

  /* A FieldSet is a combination of an Integer, a Date and a String - The strings can very in length uniformly*/
  /* We use this set of 3 fields repeated in our documents */

  public InsertTest(MongoClient client, Document config, long nThreads, long threadNo) {
    super(client, config, nThreads, threadNo);

    database = mongoClient.getDatabase(testConfig.getString("database"));
    collection = database.getCollection(testConfig.getString("collection"), RawBsonDocument.class);
    Document variant = config.get("variant", Document.class);

    // Allow variation of batch size

    writeBatchSize = testConfig.getInteger("writeBatchSize", 1000);

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

    // Depth is used to indcate the maximum number of fields at a given level
    // Min is 5 - so with 5 we will take a new level every 5 at this level
    bigrandomString = Utils.BigRandomText(SAMPLESTRINGLENGTH);
    maxFieldsPerObject =
        Objects.requireNonNullElse(testConfig.getInteger("maxFieldsPerObject"), 200);
  }

  public void run() {

    // Try to have defaults

    int docsPerThread = (int) (totalDocsToInsert / nThreads);
    List<RawBsonDocument> batch = new ArrayList<>();
    int size = 0;
    for (int doc = 0; doc < docsPerThread; doc++) {
      RawBsonDocument d = createDocument();
      batch.add(d);
      size = size + d.getByteBuffer().remaining();
      if (batch.size() >= writeBatchSize) {
        collection.insertMany(batch);
        batch.clear();
      }
    }
    if (batch.size() >= 0) {
      collection.insertMany(batch);
      batch.clear();
    }
  }

  // Actually exactly not dropping data in the simple version
  // Maybe a V2 that does a second insert into same volleciton though
  public void GenerateData() {
    logger.info("No data generation was required");
  }

  // Reset is called for each variant

  public void TestReset() {
    logger.info("Dropping " + collection.getNamespace().toString());
    collection.drop();
  }

  // WarmCache is called before each rune
  public void WarmCache() {
    logger.info("No cache warm up was required");
  }

  // TODO - Figure out what wa want to control in our document
  // Size, NFields and Depth I guess
  // We want ot be able to generate data very fast but pseiudo random

  private RawBsonDocument createDocument() {
    BasicOutputBuffer buffer = new BasicOutputBuffer();
    BsonWriter writer = new BsonBinaryWriter(buffer);
    int fNo = 1;

    int currentDepth = 0;

    writer.writeStartDocument();
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
