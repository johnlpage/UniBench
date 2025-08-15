package com.mongodb.solcon;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BenchmarkController {
  private static final Logger logger = LoggerFactory.getLogger(BenchmarkController.class);
  ResultRecorder resultRecorder;
  MongoClient mongoClient;
  Document bmConfig;
  AtlasClusterManager atlasClusterManager;

  BenchmarkController() {
    resultRecorder = new ResultRecorder();
    atlasClusterManager = new AtlasClusterManager();
  }

  void connectToMongoDB() {
    try {
      logger.info("Connecting to MongoDB...");
      String mongoURI = System.getenv("MONGO_URI");
      if (mongoURI == null) {
        logger.error("MONGO_URI not defined");
        System.exit(1);
      }
      mongoClient = MongoClients.create(mongoURI);
      logger.info("Testing instance {}", mongoURI);
      Document rval = mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
      logger.info(rval.toJson());
    } catch (Exception e) {
      logger.error("An error occurred while connecting to MongoDB", e);
      System.exit(1);
    }
  }

  public void runBenchmark(String configFile) {
    bmConfig = readConfigFile(configFile);

    /* Ensure we have a cluster to test with */
    String atlasInstanceType = bmConfig.getString("atlasInstanceType");
    final String testClusterName = "UniBenchTemp";

    if (atlasInstanceType != null) {
      try {
        if (atlasInstanceType.equals("none")) {
          atlasClusterManager.deleteCluster(testClusterName);
          System.out.println("Deleted Cluster " + testClusterName);
          System.exit(0);
        } else {
          atlasClusterManager.createCluster(testClusterName, atlasInstanceType);
        }

      } catch (Exception e) {
        logger.error("An error occurred while starting a cluster to MongoDB", e);
        System.exit(1);
      }
    }

    /* Read in the top level config and execute all tests */
    connectToMongoDB();
    for (String testConfigFile : bmConfig.getList("tests", String.class)) {
      runTest(testConfigFile);
    }
  }

  Document readConfigFile(String fileName) {
    Document configFile;
    try {
      String configString = new String(Files.readAllBytes(Paths.get(fileName)));
      configFile = Document.parse(configString);
    } catch (Exception ex) {
      logger.error("ERROR IN CONFIG: {} {} ", fileName, ex.getMessage());
      return null;
    }
    return configFile;
  }

  @SuppressWarnings("unchecked")
  void runTest(String testConfigFile) {
    Document testConfig = readConfigFile(testConfigFile);
    testConfig.append(
        "filename", testConfigFile.replaceAll(".*[/\\\\]([^./\\\\]+)\\.[^/\\\\]*$", "$1"));

    try {
      @SuppressWarnings("rawtypes")
      Class testClass = Class.forName(testConfig.getString("testClassName"));
      testConfig.put("variant", new Document());
      BaseMongoTest test =
          (BaseMongoTest)
              testClass.getDeclaredConstructors()[0].newInstance(mongoClient, testConfig, 0, 0);

      // If data needs generated (or verified), do it in the test class here
      test.GenerateData();
      // If the data exists wut we want to do any warm-up of caches, then do it here
      test.WarmCache();

      int numberOfThreads = testConfig.getInteger("numberOfThreads", 20);

      for (Document variant : testConfig.getList("variants", Document.class)) {
        testConfig.put(
            "variant",
            variant); // Set the mode parameter to whatever mode we want - this cna be used to
        // set groups of parameters - like running against an empty or prepopulated collection
        logger.info("Running variant {}", variant.toJson());
        /* Use this for any pre-run cleanup that's required */
        test.TestReset();

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        // If the test config defines a warmup routine, run the test once without measuring.

        if (testConfig.getBoolean("warmup", true)) {
          logger.info("Test Warmup Run");
          testConfig.put("warmup", true);
          runTestsInParallel(testConfig, mongoClient, testClass, numberOfThreads, executorService);
        }

        testConfig.put("warmup", false);

        // Used to capture ServerStatus
        Document statusBefore;
        Document statusAfter;

        assert mongoClient != null;
        statusBefore = mongoClient.getDatabase("admin").runCommand(new Document("serverStatus", 1));

        executorService = Executors.newFixedThreadPool(numberOfThreads);

        logger.info("Test Live Run {} threads", numberOfThreads);
        Date startTime = new Date();
        runTestsInParallel(testConfig, mongoClient, testClass, numberOfThreads, executorService);
        Date endTime = new Date();
        long timeTaken = endTime.getTime() - startTime.getTime();

        statusAfter = mongoClient.getDatabase("admin").runCommand(new Document("serverStatus", 1));
        logger.info("Test Complete");

        logger.info("Time: {}s", timeTaken / 1000);
        resultRecorder.recordResult(
            bmConfig, testConfig, variant, statusBefore, statusAfter, startTime, endTime);
      }

    } catch (Exception e) {
      logger.error("An error occurred: {}", e.getMessage());
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
  }

  private void runTestsInParallel(
      Document testConfig,
      MongoClient mongoClient,
      Class<BaseMongoTest> testClass,
      int numberOfThreads,
      ExecutorService executorService)
      throws InstantiationException,
          IllegalAccessException,
          java.lang.reflect.InvocationTargetException,
          InterruptedException {

    for (int threadNo = 0; threadNo < numberOfThreads; threadNo++) {
      BaseMongoTest t =
          (BaseMongoTest)
              testClass.getDeclaredConstructors()[0].newInstance(
                  mongoClient, testConfig, numberOfThreads, threadNo);

      executorService.submit(t);
    }
    executorService.shutdown();
    //noinspection ResultOfMethodCallIgnored
    executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
  }
}
