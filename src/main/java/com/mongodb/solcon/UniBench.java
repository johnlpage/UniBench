package com.mongodb.solcon;

import java.util.logging.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UniBench {
  private static final Logger logger = LoggerFactory.getLogger(UniBench.class);

  public static void main(String[] args) {
    LogManager.getLogManager().reset();
    BenchmarkController bmc = new BenchmarkController();

    if (args.length < 1) {
      System.out.println("Usage: java -jar UniBench.jar <config.json>");
      return;
    }
    bmc.runBenchmark(args[0]);
  }
}
