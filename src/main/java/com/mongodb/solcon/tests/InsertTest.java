package com.mongodb.solcon.tests;
import com.mongodb.solcon.BaseMongoTest;
import com.mongodb.solcon.Utils;
import org.bson.*;
import org.bson.io.BasicOutputBuffer;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* This tests measures inserting data into a MognoDB cluster, It supports the following test specific parameters.
    * ndocs - How much data to insert
    * docsize ( in KB )
    * flatness
    * numindexes
 */
public class InsertTest extends BaseMongoTest {
  private static final Logger logger = LoggerFactory.getLogger(InsertTest.class);
  MongoDatabase database;
  MongoCollection<RawBsonDocument> collection;

  int maxFieldsPerObject=200;
  int docsizeBytes;
  final int MEANSTRINGLENGTH=32;
  final int SAMPLESTRINGLENGTH = 1024*1024;

  String bigrandomString;

  /* A FieldSet is a combination of an Integer, a Date and a String - The strings can very in length uniformly*/
  /* We use this set of 3 fields repeated in our documents */


  public InsertTest(MongoClient client, Document config, long nThreads, long threadNo) {
    super(client, config,nThreads, threadNo);

    database = mongoClient.getDatabase(testConfig.getString("database"));
    collection = database.getCollection(testConfig.getString("collection"), RawBsonDocument.class);
    // Approx Doc Size in bytes

    docsizeBytes  = (int) (testConfig.getDouble("docSizeKB") * 1024);
    //Depth is used to indcate the maximum number of fields at a given level
    //Min is 5 - so with 5 we will take a new level every 5 at this level
    bigrandomString = Utils.BigRandomText(SAMPLESTRINGLENGTH);
    maxFieldsPerObject = Objects.requireNonNullElse(testConfig.getInteger("maxFieldsPerObject"),200);
  }

  public void run() {

    int totalDocsToInsert  = testConfig.getInteger("totalDocsToInsert");
    final int BATCHSIZE=1000;

    int docsPerThread = (int) (totalDocsToInsert / nThreads);
    List<RawBsonDocument> batch = new ArrayList<>();

    for (int doc = 0; doc < docsPerThread; doc++) {
     RawBsonDocument d = createDocument();
     batch.add(d);
     if(batch.size() >= BATCHSIZE) {
       collection.insertMany(batch);
       batch.clear();
     }
    }
    if(batch.size() >= 0) {
      collection.insertMany(batch);
      batch.clear();
    }
  }

  // Actually exactly not dropping data in the simple version
  // Maybe a V2 that does a second insert into same volleciton though
  public void GenerateData() {
    logger.info("No data generation was required");
  }

  public void TestReset() {
    String mode = testConfig.getString("mode");
    logger.info("Test Reset called [{}]",mode);
    if (mode.equals("empty")) {
      logger.info("Dropping {}", collection.getNamespace().toString());
      collection.drop();
    }
  }

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
    while(buffer.size() < docsizeBytes) {
      writer.writeInt32("intfield" + fNo,random.nextInt(100_000));
      writer.writeDateTime(("datefield" + fNo), 1754917200011L + random.nextLong(10_000_000_000L)); //TODO Bound
      // these to
      // sensible values
      int stringLength = random.nextInt(MEANSTRINGLENGTH) + 1;
      int offset = random.nextInt(SAMPLESTRINGLENGTH-MEANSTRINGLENGTH);
      String example = bigrandomString.substring(offset,offset+stringLength);
      writer.writeString("stringfield" + fNo,example);
      fNo++;
    }
    writer.writeEndDocument();

    return new RawBsonDocument(buffer.toByteArray());
  }
}
