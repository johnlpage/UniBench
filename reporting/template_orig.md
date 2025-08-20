# 🚧🚧 UNDER CONSTRUCTION 🚧🚧

Ignore content for now.

# MongoDB Performance Tables

| Author      | John Page  |
|-------------|------------|
| **Date**    | 2020-09-22 |
| **Version** | 0.1        |

## About this document

This document provides an indication for the expected performance of MongoDB in
specific tasks. Each table demonstrates how performance is affected by varying
parameters, such as adding an additional index. The focus is strictly on
database
performance; assume the client is interacting with MongoDB optimally and there
are no network constraints between the client and the server.

Unless otherwise stated, the tests were carried out on a 3-node replica set in
MongoDB Atlas using an M30 instance (2 vCPUs, 8GB RAM, and 2GB configured as
cache) with 3,000 IOPS on Amazon Web Services. Writes use a majority write
concern, and all reads are from the primary node.

MongoDB scales vertically by utilizing more powerful hardware or horizontally by
introducing additional replica sets. Many workloads can scale linearly to
thousands
of times the throughput shown here.

The purpose of this document is to help readers understand expected MongoDB
performance. It can be used to verify your own application's
performance or guide decisions about sizing MongoDB to meet specific
performance goals.

Although it is not possible to document every combination of operations or
workload mix, the provided results should allow you to make reasonable
inferences. Notes are included for each test to highlight significant results.
The test framework is also relatively easy to adapt to your own specific needs.

Performance depends on available CPU, disk I/O capability, and RAM. Adequate RAM
can reduce disk I/O requirements through caching and efficient write
amortisation.
While the examples assume an optimal client setup, certain client-side
operations result in additional server workload, this is demonstrated in some
examples.

Explanations of test results and the underlying low-level behavior affecting
them are provided where relevant.

# Data Ingestion

This section discusses the process of importing data into MongoDB, whether it is
entirely new data or a mix of new, modified, and identical (existing) records.
Existing records are identified by a known key field

## Impact of document size on insert speed

### Description

This shows how the document size impacts the speed in MB/s when using `insert`
operations to add documents and assign them a primary key. In the test 24 GB of
data was bulk inserted into an empty collection. The only index is the _id index
with the default ObjectID(), in this was only a small set of database blocks
are being written to at any one time, so nearly all writes to the database are
appended with minimal random I/O and maximal use of IOPS.

Data like this is very quick to write, but without additional indexes it can
only be efficiently retrieved using a single kay and is usually only useful for
logging or cachhig use cases.

### Performance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_docsize"}},
    {"$set": { "totalKB" : { "$multiply" : [ "$variant.docSizeKB", "$variant.totalDocsToInsert"]}}},
    {"$project": {
                  "docSizeKB": "$variant.docSizeKB",
                  "totalMB": {"$round":{ "$divide":  [ "$totalKB",1024]} },
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "MBperSecond": { "$round" : [ {"$divide": ["$totalKB", "$duration"]},2]},
                  "DocsPerSecond" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$variant.totalDocsToInsert"]}, "$duration"]}]}
    }},
    {"$sort":{ "docSizeKB": 1}}],
    "columns": ["docSizeKB", "durationS","totalMB","DocsPerSecond","MBperSecond"],
    "headers": ["Document Size (KB)", "Time Taken (s) ","Data Loaded (MB)", "Speed (docs/s)", "Speed (MB/s)"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_docsize" }},
    { "$set" :  { "durationS": {"$round":{"$divide":[ "$duration",1000]}}}},
    { "$set" : { "userCPU": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "SYSTEM_NORMALIZED_CPU_USER"]}}}}}},
    { "$set" : { "iowaitCPU": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "SYSTEM_NORMALIZED_CPU_IOWAIT"]}}}}}},
    { "$set" : { "kernelCPU": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "SYSTEM_NORMALIZED_CPU_KERNEL"]}}}}}},
    { "$set" : { "allCPU" : { "$zip" : {"inputs":[ "$kernelCPU.dataPoints.value", "$userCPU.dataPoints.value"]}}}},
    { "$set" : { "cpuReadings": { "$map" : { "input": "$allCPU", "in" : { "$sum": "$$this"}}}}},

    { "$set" : { "cacheReadIn" : {"$subtract" : [ "$after_status.wiredTiger.cache.pages read into cache", "$before_status.wiredTiger.cache.pages read into cache"]}}},

    { "$set" : { "cacheWriteOut" :{"$subtract" : [    "$after_status.wiredTiger.block-manager.bytes written", 
                                                                            "$before_status.wiredTiger.block-manager.bytes written"]}}},

    { "$set" : { "journalWrite" : {"$subtract" : [ "$after_status.wiredTiger.log.total size of compressed records", "$before_status.wiredTiger.log.total size of compressed records"]}}},

    { "$set" : { "totalIops" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_IOPS_TOTAL"]}}}}}},
 {   "$set" : { "meanIops" : {"$round": { "$avg" : "$totalIops.dataPoints.value"}}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": { "$divide" : [ "$journalWrite", "$duration"]}}}},

    {"$project": {
                 "userCPU":1,"meanIops":1,
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
 { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
{"$sort":{ "docSizeKB": 1}}],

    "columns": ["docSizeKB","meancpu","iowait","cachePageReadPerSecondKB","compressedDataPerSecondKB","journalPerSecondKB","estimatedIOPS","meanIops" ],
    "headers": ["Document Size (KB)","CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","Write from Cache (KB/s)","Write to WAL (KB/s)", "Predicted IOPS","Actual mean IOPS"]
}
-->  

### Analysis

Smaller documents generate more primary key index entries, which introduces
overhead even when these entries are sequential. These tests use AWS GP3 storage
volumes with 3,000 IOPS and 125 MiB/s write speed. Each inserted document
requires insertion into the Oplog, Write-Ahead Log (WAL), and the collection.
Even with compression, this workflow is likely constrained by IOPS.

## Impact of client write batch size on write speed

### Description

MongoDB lets you send multiple write operations in a single network request.
Using methods like insertOne(), replaceOne(), or save() in Spring Data results
in each document being sent individually. This incurs network round-trip latency
for each document and requires independent durability checks. These checks
involve network round-trips to secondary nodes and periodic disk flushes on both
primaries and secondaries.

Batching write operations combines these costs across multiple documents,
reducing the overall overhead. While larger batches may slightly increase
latency due to processing time, they generally lead to significantly higher
throughput.

Single writes can improve performance by increasing concurrency through
asynchronous operations or multi-threading, but batching documents remains far
more efficient.

This test evaluates the ingestion of 2GB of data (512,000 documents of 4KB size)
while varying the network write batch sizes to illustrate the importance of
batching.

### Performance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_batchsize"}},
    {"$set": { "totalKB" : { "$multiply" : [ "$test_config.docSizeKB", "$test_config.totalDocsToInsert"]}}},
    {"$project": {
                  "batchsize": "$variant.writeBatchSize","totalKB":1,"totalDocsToInsert":1,
                  "totalMB": {"$round":{ "$divide":  [ "$totalKB",1024]} },
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "MBperSecond": { "$round" : [ {"$divide": ["$totalKB", "$duration"]},2]},
                  "DocsPerSecond" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$test_config.totalDocsToInsert"]}, "$duration"]}]}
    }},
    {"$sort":{ "docSizeKB": 1}}],
    "columns": ["batchsize", "durationS","totalMB","DocsPerSecond","MBperSecond"],
    "headers": ["Write Batch Size", "Time Taken (s) ","Data Loaded (MB)", "Speed (docs/s)", "Speed (MB/s)"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_batchsize" }},
    { "$set" :  { "durationS": {"$round":{"$divide":[ "$duration",1000]}}}},
    { "$set" : { "userCPU": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "SYSTEM_NORMALIZED_CPU_USER"]}}}}}},
    { "$set" : { "iowaitCPU": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "SYSTEM_NORMALIZED_CPU_IOWAIT"]}}}}}},
    { "$set" : { "kernelCPU": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "SYSTEM_NORMALIZED_CPU_KERNEL"]}}}}}},
    { "$set" : { "allCPU" : { "$zip" : {"inputs":[ "$kernelCPU.dataPoints.value", "$userCPU.dataPoints.value"]}}}},
    { "$set" : { "cpuReadings": { "$map" : { "input": "$allCPU", "in" : { "$sum": "$$this"}}}}},

    { "$set" : { "cacheReadIn" : {"$subtract" : [ "$after_status.wiredTiger.cache.pages read into cache", "$before_status.wiredTiger.cache.pages read into cache"]}}},

    { "$set" : { "cacheWriteOut" :{"$subtract" : [    "$after_status.wiredTiger.block-manager.bytes written", 
                                                                            "$before_status.wiredTiger.block-manager.bytes written"]}}},

    { "$set" : { "journalWrite" : {"$subtract" : [ "$after_status.wiredTiger.log.total size of compressed records", "$before_status.wiredTiger.log.total size of compressed records"]}}},

    { "$set" : { "totalIops" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_IOPS_TOTAL"]}}}}}},
 {   "$set" : { "meanIops" : {"$round": { "$avg" : "$totalIops.dataPoints.value"}}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": { "$divide" : [ "$journalWrite", "$duration"]}}}},

    {"$project": {
 "batchsize": "$variant.writeBatchSize",
                 "userCPU":1,"meanIops":1,
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
 { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
{"$sort":{ "docSizeKB": 1}}],

    "columns": ["batchsize","meancpu","iowait","cachePageReadPerSecondKB","compressedDataPerSecondKB","journalPerSecondKB","estimatedIOPS","meanIops" ],
    "headers": ["Write Batch Size","CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","Write from Cache (KB/s)","Write to WAL (KB/s)", "Predicted IOPS","Actual mean IOPS"]
}
-->  

## To Add

The following tests will be covered in future sections:

* Ingesting Data: Document size and performance impact
* Batching vs Single Insert: Performance and overhead comparison
* ObjectId vs UUID: Effects on indexing and resource usage
* NUM Index and Cache: Optimization considerations
* Index Type: Implications for query performance
* Replace Tests: Replace versus update mechanics
* I/O Operations: Detailed discussion on IOPS and disk flushes
* Updates: Performance variations based on index configurations
* Retrieval Tests: Single results, sets, pagination, and partial indexes
* Aggregation: Performance metrics for $group and $lookup operations
* Deletion Tests: Efficiency of removal operations