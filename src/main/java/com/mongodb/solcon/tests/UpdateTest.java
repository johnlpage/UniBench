package com.mongodb.solcon.tests;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.solcon.BaseMongoTest;
import com.mongodb.solcon.DocumentFactory;
import com.mongodb.solcon.Utils;
import org.apache.commons.lang3.RandomUtils;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateTest extends BaseMongoTest {
    private static final Logger logger = LoggerFactory.getLogger(UpdateTest.class);

    final int SAMPLESTRINGLENGTH = 1024 * 1024;
    MongoDatabase database;
    MongoCollection<RawBsonDocument> collection;
    int maxFieldsPerObject;
    String bigrandomString;
    DocumentFactory docFactory = null;

    int docsizeBytes = 2048;

    public UpdateTest(MongoClient client, Document config, long nThreads, long threadNo, ConcurrentHashMap<String, Object> testReturnInfo) {
        super(client, config, nThreads, threadNo, testReturnInfo);

        database = mongoClient.getDatabase(testConfig.getString("database"));
        collection = database.getCollection(testConfig.getString("collection"), RawBsonDocument.class);
        parseTestParams();

        bigrandomString = Utils.BigRandomText(SAMPLESTRINGLENGTH);
        maxFieldsPerObject =
                Objects.requireNonNullElse(testConfig.getInteger("maxFieldsPerObject"), 200);

        // Approx Doc Size in bytes - get from top level unless in variant
        if (testConfig.getDouble("docSizeKB") != null) {
            docsizeBytes = (int) (testConfig.getDouble("docSizeKB") * 1024);
        }

        docFactory = new DocumentFactory(threadNo, "OBJECTID", docsizeBytes, maxFieldsPerObject);
    }

    void parseTestParams() {
    }

    /**
     * Generates a MongoDB schema validator based on a sample RawBsonDocument
     */
    private Document generateSchemaValidator(RawBsonDocument sampleDoc) {
        CodecRegistry codecRegistry = MongoClientSettings.getDefaultCodecRegistry();
        BsonDocument bsonDoc = sampleDoc.decode(codecRegistry.get(BsonDocument.class));
        Document properties = new Document();

        for (Map.Entry<String, BsonValue> entry : bsonDoc.entrySet()) {
            String fieldName = entry.getKey();
            BsonValue value = entry.getValue();
            Document fieldSchema = new Document();

            switch (value.getBsonType()) {
                case OBJECT_ID:
                    fieldSchema.put("bsonType", "objectId");
                    break;
                case STRING:
                    fieldSchema.put("bsonType", "string");
                    // Set a generous max length that will always pass
                    fieldSchema.put("maxLength", 1_000_000);
                    break;
                case INT32:
                    fieldSchema.put("bsonType", "int");
                    // Set a range that will always pass
                    fieldSchema.put("minimum", Integer.MIN_VALUE);
                    fieldSchema.put("maximum", Integer.MAX_VALUE);
                    break;
                case INT64:
                    fieldSchema.put("bsonType", "long");
                    fieldSchema.put("minimum", Long.MIN_VALUE);
                    fieldSchema.put("maximum", Long.MAX_VALUE);
                    break;
                case DOUBLE:
                    fieldSchema.put("bsonType", "double");
                    fieldSchema.put("minimum", -Double.MAX_VALUE);
                    fieldSchema.put("maximum", Double.MAX_VALUE);
                    break;
                case DATE_TIME:
                    fieldSchema.put("bsonType", "date");
                    break;
                case BOOLEAN:
                    fieldSchema.put("bsonType", "bool");
                    break;
                case BINARY:
                    fieldSchema.put("bsonType", "binData");
                    break;
                case ARRAY:
                    fieldSchema.put("bsonType", "array");
                    break;
                case DOCUMENT:
                    fieldSchema.put("bsonType", "object");
                    break;
                case NULL:
                    fieldSchema.put("bsonType", "null");
                    break;
                default:
                    // For any other type, just allow it
                    logger.debug("Unknown BSON type for field {}: {}", fieldName, value.getBsonType());
                    continue;
            }

            properties.put(fieldName, fieldSchema);
        }

        return new Document("$jsonSchema",
                new Document("bsonType", "object")
                        .append("properties", properties)
        );
    }

    /**
     * Applies or removes schema validation based on config
     */
    private void applySchemaValidation(boolean enable) {
        if (enable) {
            // Create a sample document to generate the schema from
            Document extraFields = new Document("_id", new ObjectId());
            extraFields.put("count", 0);
            RawBsonDocument sampleDoc = docFactory.createDocument(extraFields);

            Document validator = generateSchemaValidator(sampleDoc);

            logger.info("Applying schema validation to collection: {}", collection.getNamespace().getCollectionName());
            logger.debug("Validator schema: {}", validator.toJson());


            database.runCommand(new Document("collMod", collection.getNamespace().getCollectionName())
                    .append("validator", validator)
                    .append("validationLevel", "moderate")
                    .append("validationAction", "error"));

            logger.info("Schema validation applied successfully");
        } else {
            logger.info("Removing schema validation from collection: {}", collection.getNamespace().getCollectionName());

            database.runCommand(new Document("collMod", collection.getNamespace().getCollectionName())
                    .append("validator", new Document())
                    .append("validationLevel", "off"));

            logger.info("Schema validation removed successfully");
        }
    }

    public void run() {
        int nUpdatesRun = 0;
        try {
            int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 100000);

            Document variant = testConfig.get("variant", Document.class);
            int nUpdates = variant.getInteger("nUpdates", 100000);
            int nFields = variant.getInteger("nFields", 1);
            int nUpdatesPerThread = Math.toIntExact(nUpdates / nThreads);
            int docRange = variant.getInteger("docRange", 100000);
            boolean expressive = variant.getBoolean("expressive", false);
            int testTimeSecsGlobal = testConfig.getInteger("testTimeSecs", 0);
            int testTimeSecsVariant = variant.getInteger("testTimeSecs", 0);
            int testTimeSecs = testTimeSecsVariant > 0 ? testTimeSecsVariant : testTimeSecsGlobal;
            if (testTimeSecs > 0 && threadNo == 0) {
                logger.info("Test time is set to {} seconds", testTimeSecs);
            }
            if (threadNo == 0) {
                logger.info(
                        "Updates Per Thread = {}, Threads = {} , nFields = {}  ",
                        nUpdatesPerThread,
                        nThreads, nFields
                );
            }
            if (nUpdatesPerThread > initialDocsToInsert) {
                logger.warn(
                        "nUpdatesPerThread (nupdates/nthreads) is greater than initialDocsToInsert - this is not allowed");
                return;
            }


            Document update;

            Document mutation = new Document();
            if (expressive) {
                for (int j = 1; j < nFields; j++) {
                    mutation.put("intfield" + j,
                            new Document("$add", Arrays.asList("$intfield" + j, 1)));
                }
                update = new Document("$set", mutation);
            } else {
                for (int j = 1; j <= nFields; j++) {
                    mutation.put("intfield" + j, 1);
                }
                update = new Document("$inc", mutation);
            }

            // If a Test Time is defined then this overrides nQueries
            long startSecs = new Date().getTime();
            if (testReturnInfo != null) {
                testReturnInfo.put("nUpdates", 0);
            }

            for (nUpdatesRun = 0; nUpdatesRun < nUpdatesPerThread || testTimeSecs > 0; nUpdatesRun++) {
                int id;

                id = RandomUtils.nextInt(1, docRange);
                Bson query = Filters.eq("_id", id);

                long now = new Date().getTime();
                if (testTimeSecs > 0 && (now - startSecs) / 1000 >= testTimeSecs) {

                    break;
                }

                UpdateResult ur;
                if (expressive) {
                    ur =
                            collection.updateOne(query, List.of(update));
                } else {
                    ur =
                            collection.updateOne(query, update);
                }
                if (ur.getModifiedCount() != 1) {
                    logger.error("Update failed for id {}", id);
                }

                logger.debug("Update Result {}", ur);

            }

        } catch (Exception e) {
            logger.error("An error occurred {}", e.getMessage());
            e.printStackTrace();
        }
        if (testReturnInfo != null) {
            Integer finalNUpdatesRun = nUpdatesRun;
            testReturnInfo.compute(
                    "nUpdates", (k, v) -> (v == null) ? finalNUpdatesRun : (Integer) v + finalNUpdatesRun);
        }
    }

    // Reset is called for each variant

    public void GenerateData() {
        parseTestParams();

        int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 100000);
        boolean documentValidation = testConfig.getBoolean("documentValidation", false);

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

        // Apply or remove schema validation after data generation
        if (threadNo == 0) {
            applySchemaValidation(documentValidation);
        }
    }

    public void TestReset() {
        parseTestParams();
        int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 100000);
        logger.info("Deleting anything that has been inserted beyond {}", initialDocsToInsert);
        collection.deleteMany(Filters.gte("_id", initialDocsToInsert));
    }

    // WarmCache is called before each run
    public void WarmCache() {
        logger.info("No cache warm up was required");
    }
}
