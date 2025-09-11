package com.mongodb.solcon;

import com.mongodb.client.MongoClient;
import org.bson.Document;

import java.util.Random;

public class BaseMongoTest implements Runnable {
  protected MongoClient mongoClient;
  protected Document testConfig;
  protected long threadNo;
  protected long nThreads;
  protected Random random; //Thread Local instance but seedable

  protected BaseMongoTest(MongoClient client, Document config,  long nThreads, long threadNo) {
    this.mongoClient = client;
    this.testConfig = config;
    this.nThreads = nThreads;
    this.threadNo = threadNo;
    this.random = new Random(threadNo); //Repeatable
  }

  public void GenerateData() {
    throw new UnsupportedOperationException("Unimplemented method 'GenerateData'");
  }

  public void WarmCache() {
  }

  public void TestReset() {
  }

  @Override
  public void run() {
    throw new UnsupportedOperationException("Unimplemented method 'run'");
  }
}
