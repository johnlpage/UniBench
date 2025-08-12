# 

## Introduction

This is a standalone single Java Jar that can launch Atlas Cluters and run multiple variants of tests against them to measure performance. It is intended to give baseline performance for Atlas vlusters and other systems and to identify the impact schema and indexing descisiona have on hardware requirements
.

#To Setup an AWS Host as a Client

* Launch instance at least as much CPU as database server
* Build the test harness

```
sudo yum install -y java-21 git maven
git clone https://github.com/johnlpage/LinkTestMany.git
export JAVA_HOME="/usr/lib/jvm/java-21-amazon-corretto"
cd LinkTestMany
mvn clean package
```

Run the test Harness

```
* export MONGO_URI="... YOUR URI & CREDS "
* export MONGO_REPORTING_URI="... YOUR URI & CREDS "
* export ATLAST_API_KEY
* export ATLAS_API_ID
* java -jar bin/UniBench.jar config.json

```