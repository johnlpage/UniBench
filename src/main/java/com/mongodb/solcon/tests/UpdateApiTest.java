package com.mongodb.solcon.tests;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.solcon.BaseMongoTest;
import com.mongodb.solcon.DocumentFactory;
import com.mongodb.solcon.Utils;
import org.apache.commons.lang3.RandomUtils;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/* In this test we want ot be able to test retrieving by an index and perhaps retrieving not by an index */

public class UpdateApiTest extends BaseMongoTest {
    private static final Logger logger = LoggerFactory.getLogger(UpdateApiTest.class);

    final int SAMPLESTRINGLENGTH = 1024 * 1024;
    MongoDatabase database;
    MongoCollection<RawBsonDocument> collection;
    int maxFieldsPerObject;
    String bigrandomString;
    DocumentFactory docFactory = null;

    int docsizeBytes = 2048;

    public UpdateApiTest(MongoClient client, Document config, long nThreads, long threadNo, ConcurrentHashMap<String, Object> testReturnInfo) {
        super(client, config, nThreads, threadNo, testReturnInfo);

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

    void parseTestParams() {
    }

    public void run() {
        int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 100000);

        Document variant = testConfig.get("variant", Document.class);
        int nUpdates = variant.getInteger("nUpdates", 100000);
        int nUpdatesPerThread = Math.toIntExact(nUpdates / nThreads);
        int percentNew = variant.getInteger("percentNew", 0);
        boolean useUpsert = variant.getBoolean("upsert", false);

        String updateMode;
        updateMode = variant.getString("updateFunction");

        if (updateMode == null) {
            updateMode = "updateOne";
        }
        if (threadNo == 0) {
            logger.info(
                    "Updates Per Thread = {}, Threads = {} PercentNew = {} Function: {}() Upsert: {} ",
                    nUpdatesPerThread,
                    nThreads,
                    percentNew,
                    updateMode,
                    useUpsert);
        }
        if (nUpdatesPerThread > initialDocsToInsert) {
            logger.warn(
                    "nUpdatesPerThread (nupdates/nthreads) is greater than initialDocsToInsert - this is not allowed");
            return;
        }
        // logger.info("Starting Update Test");
        int i;
        for (i = 0; i < nUpdatesPerThread; i++) {

            boolean isNew = (percentNew > 0) && (RandomUtils.nextInt(0, 100) < percentNew);
            int id;
            if (isNew) {
                id = (int) (initialDocsToInsert + (threadNo * initialDocsToInsert) + i);
            } else {
                id = RandomUtils.nextInt(0, initialDocsToInsert);
            }

            Document extraFields = new Document("_id", id);
            RawBsonDocument fullDoc = docFactory.createDocument(extraFields);

            if (updateMode.equalsIgnoreCase("UpdateOne")) {
                UpdateOptions options = new UpdateOptions();
                if (useUpsert) {
                    options.upsert(true);
                }
                UpdateResult ur =
                        collection.updateOne(
                                Filters.eq("_id", id),
                                Updates.combine(Updates.inc("count", 1), Updates.setOnInsert(fullDoc)),
                                options);
                logger.debug("Update Result {}", ur.toString());
                if (ur.getMatchedCount() == 0 && useUpsert == false) {
                    logger.debug("Falling back to manual insert for id {}", id);
                    collection.insertOne(fullDoc); // The Non upsert Route
                }
            } else if (updateMode.equalsIgnoreCase("FindOneAndUpdate")) {
                FindOneAndUpdateOptions options =
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
                if (useUpsert) {
                    options.upsert(true);
                }
                RawBsonDocument ur =
                        collection.findOneAndUpdate(
                                Filters.eq("_id", id),
                                Updates.combine(Updates.inc("count", 1), Updates.setOnInsert(fullDoc)),
                                options);

                if (ur == null && useUpsert == false) {
                    logger.debug("Falling back to manual insert for id {}", id);
                    collection.insertOne(fullDoc); // The Non upsert Route
                }
            } else {
                logger.error("Unknown Update Mode {}", updateMode);
            }
        }
        // logger.info("Finished Update Test completed {} updates", i);
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
        int initialDocsToInsert = testConfig.getInteger("initialDocsToInsert", 100000);
        logger.info("Deleting anything that has been inserted beyond {}", initialDocsToInsert);
        collection.deleteMany(Filters.gte("_id", initialDocsToInsert));
    }

    // WarmCache is called before each rune
    public void WarmCache() {
        logger.info("No cache warm up was required");
    }


}
