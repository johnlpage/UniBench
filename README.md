#             

## Introduction

This is a standalone single Java Jar that can launch Atlas Cluters and run
multiple variants of tests against them to
measure performance. It is intended to give baseline performance for Atlas
vlusters and other systems and to identify
the impact schema and indexing descisiona have on hardware requirements
.

# To Setup an AWS Host as a Client

* Launch instance at least as much CPU as database server

```shell
// Edit to put at LEAST your key in
launchechec2.sh
```

* Build the test harness

```
sudo yum install -y java-21 git maven
```

Then

```
git clone https://github.com/johnlpage/UniBench.git
export JAVA_HOME="/usr/lib/jvm/java-21-amazon-corretto"
cd UniBench
mvn clean package


```

# Run the test Harness

```shell
export MONGO_URI=<URI OF CLUSTER YOU WILL BE TESTING>
export MONGO_REPORTING_URI=<URI OF CLUSTER TO SAVE RESULTS IN>
export ATLAS_PROJECT_ID=XXXX
export ATLAS_PUBLIC_KEY=XXXX
export ATLAS_PRIVATE_KEY=XXXXX
java -jar bin/UniBench.jar benchmark.json
```

# When finished take down the Atlas cluster

```shell


java -jar bin/UniBench.jar teardown.json
```
