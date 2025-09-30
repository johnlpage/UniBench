package com.mongodb.solcon;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BenchmarkController {
  private static final Logger logger = LoggerFactory.getLogger(BenchmarkController.class);
  final String testClusterName = "UniBenchTemp";
  ResultRecorder resultRecorder;
  MongoClient mongoClient;
  Document bmConfig;
  AtlasClusterManager atlasClusterManager;
  boolean isCloudAtlas;

  BenchmarkController() {
    resultRecorder = new ResultRecorder();
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
      Document rval = mongoClient.getDatabase("admin").runCommand(new Document("hello", 1));

      logger.info("{}", rval.toJson());

    } catch (Exception e) {
      logger.error("An error occurred while connecting to MongoDB", e);
      System.exit(1);
    }
  }

  public void runBenchmark(String configFile) {
    bmConfig = readConfigFile(configFile);

    /* Ensure we have a cluster to test with */
    String atlasInstanceType = bmConfig.getString("atlasInstanceType");
    isCloudAtlas = atlasInstanceType != null;
    if (isCloudAtlas) {
      atlasClusterManager = new AtlasClusterManager();
    }

    if (isCloudAtlas) {
      try {
        int iops = bmConfig.getInteger("atlasIOPS", 3000);
        int disksize = bmConfig.getInteger("atlasDiskSizeGB", 60);
        String diskType = bmConfig.getString("atlasDiskType");
        if (diskType == null) {
          diskType = "STANDARD ";
        }
        atlasClusterManager.modifyCluster(
            testClusterName, atlasInstanceType, diskType, disksize, iops);

      } catch (Exception e) {
        logger.error("An error occurred while starting a cluster to MongoDB", e);
        System.exit(1);
      }
    }

    Instant startTime = Instant.now().minus(Duration.ofMinutes(10));
    Instant endTime = Instant.now();
    if (isCloudAtlas) {
      try {
        // Test we have working metrics - might need access list modified

        atlasClusterManager.getClusterPrimaryMetrics(
            testClusterName, startTime.toString(), endTime.toString());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    /* Read in the top level config and execute all tests */
    connectToMongoDB();
    if (bmConfig.getList("tests", String.class) != null) {
      for (String testConfigFile : bmConfig.getList("tests", String.class)) {
        runTest(testConfigFile);
      }
    }
    // Tear down at end of tests if specified - often this will be in own file
    if (bmConfig.getBoolean("teardownAtlas", false)) {
      try {
        atlasClusterManager.deleteCluster(testClusterName);
        System.out.println("Deleted Cluster " + testClusterName);
        System.exit(0);
      } catch (Exception e) {
        logger.error("An error occurred while deleting a cluster from Atlas", e);
        System.exit(1);
      }
    }
  }

  Document readConfigFile(String fileName) {
    Document configFile = null;
    try {
      String configString = new String(Files.readAllBytes(Paths.get(fileName)));
      configFile = Document.parse(configString);
    } catch (Exception ex) {
      logger.error("ERROR IN CONFIG: {} {} ", fileName, ex.getMessage());
      System.exit(1);
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
        if (isCloudAtlas && variant.containsKey("instance")) {

          Document instance = variant.get("instance", Document.class);
          String atlasInstanceType = instance.getString("atlasInstanceType");
          int iops = instance.getInteger("atlasIOPS", 3000);
          int disksize = instance.getInteger("atlasDiskSizeGB", 60);
          String diskType = instance.getString("atlasDiskType");
          if (diskType == null) {
            diskType = "STANDARD ";
          }
          atlasClusterManager.modifyCluster(
              testClusterName, atlasInstanceType, diskType, disksize, iops);
        }
        /* We can change threads by variant */

        test.TestReset();
        if (variant.containsKey("numberOfThreads")) {
          numberOfThreads = variant.getInteger("numberOfThreads");
        }
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
        Instant startTime = Instant.now();
        runTestsInParallel(testConfig, mongoClient, testClass, numberOfThreads, executorService);
        Instant endTime = Instant.now();
        long timeTaken = Duration.between(startTime, endTime).toMillis();
        logger.info("Test Complete");

        logger.info("Time: {}s", timeTaken / 1000);
        if (isCloudAtlas) {

          Document metrics =
              atlasClusterManager.getClusterPrimaryMetrics(
                  testClusterName, startTime.toString(), endTime.toString());

          statusAfter =
              mongoClient.getDatabase("admin").runCommand(new Document("serverStatus", 1));

          resultRecorder.recordResult(
              bmConfig,
              testConfig,
              variant,
              statusBefore,
              statusAfter,
              metrics,
              startTime,
              endTime);
        }
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
