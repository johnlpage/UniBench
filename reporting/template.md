# MongoDB Performance Tables

| Author      | John Page   |
|-------------|-------------|
| **Date**    | 2025-12-09  |
| **Version** | 0.9  (Beta) |

## TLDR;

This document shows the performance you can expect from MongoDB on a given
hardware infrastructure. You can use it to compare the performance of your own
client code and to determine the hardware required for a given performance
target. It quantifies the impact of various parameters on the performance of
MongoDB.

## Table of Contents

<!-- TOC -->

* [Introduction](#introduction)
* [Data Ingestion](#data-ingestion)
    * [Expected insert speed by document size](#expected-insert-speed-by-document-size)
    * [Impact of client write batch size on write speed](#impact-of-client-write-batch-size-on-write-speed)
    * [Impact of primary key type on write speed](#impact-of-primary-key-type-on-write-speed)
    * [Impact of number of indexes on write speed](#impact-of-number-of-indexes-on-write-speed)
    * [Standard vs Provisioned IOPS and Throughput](#standard-vs-provisioned-iops-and-throughput)
    * [Impact of Instance Size on write performance](#impact-of-instance-size-on-write-performance)
    * [Impact of hot documents and concurrency on write performance](#impact-of-hot-documents-and-concurrency-on-write-performance)
    * [Comparison of using UpdateOne vs. FindOneAndUpdate and upsert vs. explicit insert](#comparison-of-using-updateone-vs-findoneandupdate-and-upsert-vs-explicit-insert)
    * [High-level comparison of querying types](#high-level-comparison-of-querying-types)
    * [Comparing the number of documents retrieved after index lookup](#comparing-the-number-of-documents-retrieved-after-index-lookup)
    * [Impact of IOPS on out-of-cache query performance](#impact-of-iops-on-out-of-cache-query-performance)
    * [To Add](#to-add)

<!-- TOC -->

## Introduction

This document shows the expected performance of MongoDB when performing a given
task. Each table shows the impact on the performance of changing individual test
parameters. For example, adding an index. This is testing only database
performance, you should assume that the client is using MongoDB optimally and
that there are no network constraints between client and server unless the test
explicitly says otherwise.

Unless otherwise specified, the database tested is a 3-node Replica Set in
MongoDB Atlas using an M40 (4 vCPU, 16GB RAM, 8GB configured as database
cache). This is using default of 3,000 Standard IOPS on Amazon Web Services
with a 200 GB disk. Writes are using write concern majority, all reads are from
the primary. The test harness is running in the same cloud provider region.

In MongoDB, you can scale vertically by using larger hardware but also
horizontally by adding more replica sets, most workloads will scale linearly to
thousands of times the performance shown here via sharding.

The intent of this document is to assist in understanding the approximate
expected performance of MongoDB. This information can be used either to verify
that your own application running on MongoDB is performing as expected or to
help you make decisions about how to configure your MongoDB instance for a given
performance target.

It is not possible to document every possible combination of operations or how a
mix of operations will interact, however, this data should allow you to infer
that. Notes will be supplied with each test where the results show something of
significance.

The performance of MongoDB or any database will depend on the available CPU and
Disk performance. Available RAM will further reduce the number of Disk
operations required by caching and amortizing write requests where safe to do
so. This assumes an ideal client as used in most of these cases, however, some
client constructs and then require more work per write operation by the server,
which is demonstrated in later examples.

The author has tried to include explanations for the results and the underlying
low-level behavior that makes them so with each example.

# Data Ingestion

This section covers getting data from outside MongoDB into MongoDB. Either data
known to be new or data where some records are new, some are modified and others
are identical to existing records; Existing in this case being determined by a
unique key field value.

## Expected insert speed by document size

### Description

This shows how the document size impacts the write speed in MB/s when using
`insert`
operations to add documents and assign them a primary key. In the test 24 GB of
data was bulk inserted into an empty collection. The only index is the _id index
with the default ObjectID().

Data like this is efficient to write, but without additional indexes it can
only be efficiently retrieved using a single kay and is usually only useful for
simple use cases.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_docsize"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

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
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": { "$divide" : [ "$journalWrite", "$duration"]}}}},
    {"$project": {
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
  {"$sort":{ "docSizeKB": 1}}],

    "columns": ["docSizeKB","meancpu","iowait","cachePageReadPerSecondKB","compressedDataPerSecondKB","journalPerSecondKB","meanIops","meanWrite","meanRead" ],
    "headers": ["Document Size (KB)","CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","Write from Cache (KB/s)","Write to WAL (KB/s)", "O/S IOPS","O/S Write (MB/s)","O/S Read (MB/s)"]
}
-->  

### Analysis

We see that we can write >100,000 1KB documents per second on what is a
relatively
small server, although with so many individual documents the overheads of
indexing
increase the CPU usage. We are also capped with these small documents by a
combination of client threads and network trips with significant time lost to
network latency.

Once we move to 4KB documents, we are writing more efficiently, the CPU drops
to <50% of our 4 cores, and the total MB/s being written rises to 125MB/s when
measured as JSON documents. The limit if MB/s written and so the inserts per
second drop proportionally with the document size.

Looking at the resource usage, we can see we are not using 100% of CPU and are
ony using about 33% of our available IOPS. We can assume we are not network or
client CPU constrained here, so what is limiting our writer throughput?

The answer is the disk throughput. With "Standard" gp3 disks we are limited to
125MB/s for any drive <170GB. This then goes up at 5MB/s per additional 10GB up
to 260GB. Thereafter, rising at 1MB/s per additional 10GB.

Out 200GB volume has a maximum write throughput of 125 + 85M + 15 = 225MB/s and
this is what limits our total write speed.

Each inserted document is compressed and then needs to be inserted in the Oplog,
the Write-Ahead Log (journal) and the collection. We can see that we are hitting
this limit even though we are using only about 33% of IOPS. Out 150MB/s of JSON
becomes 450MB/s of uncompressed writes and ~225MB/s of compressed writes

### Key Takeaways

* It is important to think of write speed in MB/s not Inserts/s.
* The limit to writes on Atlas is most commonly disk throughput — not IOPS.
* Small disks (<170GB) are limited to 125MB/s, which is about 60MB/s of writes.
* Each CPU core can write approx 60MB/s of data.

## Impact of client write batch size on write speed

### Description

In this test we insert 24GB of data (6.2 Million 4 KB documents) using
differing network write batch sizes to illustrate the impact batching writes for
ingestion. We use 48 threads loading in parallel.

MongoDB allows you to send multiple write operations to the database in a single
network request. From MongoDB 8.0 onwards these can even be written to different
collections. Conversely, when using simple insertOne(), replaceOne() (Or save()
in Spring Data) then each document is sent individually. This not only incurs a
network round trip per document but also needs each document to independently
wait for durability, awaiting a periodic disk flush on the secondary and
primary.

When you send multiple write operations as a single network call, then the
overhead of the durability is shared between all the documents; there can be a
little cpu overhead for a larger batch whilst waiting for the whole batch to be
processed, this still results in much higher throughput, albeit with some
additional latency.

When processing single writes or smaller batches, you can use more threads/async
to increase concurrency, but it is still far less efficient than even small
batches.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_batchsize"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

### Performance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_batchsize"}},
    {"$set": { "totalKB" : { "$multiply" : [ "$test_config.docSizeKB", "$test_config.totalDocsToInsert"]}}},
  { "$set" : { "opTimeWrites": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "OP_EXECUTION_TIME_WRITES"]}}}}}},
    { "$set" : { "opTimeWrites" : { "$filter": {  "input" : "$opTimeWrites.dataPoints", "cond": { "$ne" : [ "$$this.value",null]}}}}},
 {   "$set" : { "opLatency" : {"$round": { "$avg" : "$opTimeWrites.value"}}}},
    {"$project": {
"opTimeWrites":1,"opLatency":1,
                  "batchsize": "$variant.writeBatchSize","totalKB":1,"totalDocsToInsert":1,
                  "totalMB": {"$round":{ "$divide":  [ "$totalKB",1024]} },
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "MBperSecond": { "$round" : [ {"$divide": ["$totalKB", "$duration"]},2]},
                  "DocsPerSecond" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$test_config.totalDocsToInsert"]}, "$duration"]}]}
    }},
    {"$sort":{ "docSizeKB": 1}}],
    "columns": ["batchsize", "durationS","totalMB","DocsPerSecond","MBperSecond","opLatency"],
    "headers": ["Write Batch Size", "Time Taken (s) ","Data Loaded (MB)", "Speed (docs/s)", "Speed (MB/s)","Average Op Latency (ms)"]
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
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": { "$divide" : [ "$journalWrite", "$duration"]}}}},
    {"$project": {"batchsize": "$variant.writeBatchSize",
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,"totalRead":1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
  {"$sort":{ "docSizeKB": 1}}],

    "columns": ["batchsize","meancpu","iowait","cachePageReadPerSecondKB","compressedDataPerSecondKB","journalPerSecondKB","meanIops","meanWrite","meanRead" ],
    "headers": ["Write batch size","CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","Write from Cache (KB/s)","Write to WAL (KB/s)", "O/S IOPS","O/S Write (MB/s)","O/S Read (MB/s)"]
}
-->  

### Analysis

With 48 threads we see that individual writes (batch size 1) are throttled by
network hops and disk flushes to only 6,000 inserts per second. By batching we
get this up to just over 32,000 inserts/second. We are at this point hitting the
225/s write limit of our Disk.

We can see that the optimal bulk insertion size here is 1,000 documents with a
slight drop in speed at 2,000. Notably, the IOPS is a little higher for the
single inserts even though the throughput is lower, this is because there are
extra writes/flushes required to make each record separately durable, there is a
lack of amortization of resources.

Batches of 1,000 are good for bulk ingestion, but in a continuous ingestion
scenario where we may be waiting in the client to build us a set to send the
~750ms latency for 1,000 documents is quite high (although with parallel threads
it still allows good throughput)—smaller batches between 10 and 100 give far
lower latency per individual write at the expense of lower throughput.

### Key Takeaways

* Avoid sending individual write to the database if latency allows.
* Consider queueing in the client and sending documents in small batches.
* When loading a large data set, use batches of between 4 and 10MB in size.
* MongoDB can process batches in parallel so do not send them serially.

## Impact of primary key type on write speed

### Description

This test looks at the impact of using an ObjectID vs. a random GUID versus an
id constructed from AccountId and Timestamp such as you might use to record a
financial transaction. This value being the uniquely indexed primary key used to
identify documents. We first insert 50 Million 1KB documents and then measure
the time to add 50 million more once the index is a non-empty state.

All Documents in MongoDB have a primary key defined as the first field in the
document, this field is always called `_id`. Where the application does not
supply it, then the client assigns a unique value. The data type of this
auto-assigned value is ObjectId. An ObjectID is a 12-byte GUID where the first 4
bytes are the time in seconds since 1970, these are therefore approximately
sequential.

In MongoDB all database indexes are BTree indexes. Inserting mostly sequential
values into a BTree means older pages of the index do not need to be accessed
for writes and new values are inserted into a small set of pages reducing the
required disk write operations.

By contrast, if a wholly random, application-assigned value is used for `_id`
like a UUID then each new value may need to read or write any part of the index
resulting in far more dirty blocks to be written to disk and more RAM required
to cache it.

In the middle ground an application assigned _id May have an initial semi-random
portion such as an account ID or Customer ID followed by a timestamp. In this
case there will be one active index page per user.

In this test case our business _id is a string starting with "ACC" followed by
5 digits for the account and 8 hex characters for the timestamp. We insert 24
Million 1KB documents and measure the speed of each variant. The insert batch
size was 1,000.

We try to keep the indexed keys of comparable size.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_idtype"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

### Performance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_idtype"}},
    {"$set": { "totalKB" : { "$multiply" : [ "$test_config.docSizeKB", "$test_config.totalDocsToInsert"]}}},
  { "$set" : { "opTimeWrites": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "OP_EXECUTION_TIME_WRITES"]}}}}}},
    { "$set" : { "opTimeWrites" : { "$filter": {  "input" : "$opTimeWrites.dataPoints", "cond": { "$ne" : [ "$$this.value",null]}}}}},
 {   "$set" : { "opLatency" : {"$round": { "$avg" : "$opTimeWrites.value"}}}},
    {"$project": {
"opTimeWrites":1,"opLatency":1,
                  "idtype": "$variant.idType","totalKB":1,"totalDocsToInsert":1,
                  "totalMB": {"$round":{ "$divide":  [ "$totalKB",1024]} },
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "MBperSecond": { "$round" : [ {"$divide": ["$totalKB", "$duration"]},2]},
                  "DocsPerSecond" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$test_config.totalDocsToInsert"]}, "$duration"]}]}
    }},
    {"$sort":{ "docSizeKB": 1}}],
    "columns": ["idtype", "durationS","totalMB","DocsPerSecond","MBperSecond","opLatency"],
    "headers": ["Primary Key format", "Time Taken (s)","Data Loaded (MB)", "Speed (docs/s)", "Speed (MB/s)","Average Op Latency (ms)"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_idtype" }},
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
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": { "$divide" : [ "$journalWrite", "$duration"]}}}},
    {"$project": {"batchsize": "$variant.writeBatchSize",
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},"idtype": "$variant.idType",
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
  {"$sort":{ "docSizeKB": 1}}],

    "columns": ["idtype","meancpu","iowait","cachePageReadPerSecondKB","compressedDataPerSecondKB","journalPerSecondKB","meanIops","meanWrite","meanRead" ],
    "headers": ["Primary Key format", "CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","Write from Cache (KB/s)","Write to WAL (KB/s)", "O/S IOPS","O/S Write (MB/s)","O/S Read (MB/s)"]
}
-->  

### Analysis

We can see from this data that where a unique identifier is required using an
ObjectID, akin to a UUID v6 is much more efficient than a random traditional
GUID. This is because the ObjectID is a 12-byte value that is approximately
sequential and therefore does not need to fetch and rewrite older index pages as
new documents are added. Although not quite as good as an ObjectId, a
well-constructed business identifier can al act as a very efficient key.

### Key Takeaways

* If you have a unique record identifier in your data that is meaningful to the
  business, use that as the primary key for your data.
* If you need a combination of fields as your primary identifier, can create a
  second unique index to use and leave the _id as the default ObjectId very
  cheaply
* Avoid using and indexing UUID/GUID types as they have bad performance in a
  BTree index.

## Impact of number of indexes on write speed

### Description

This test shows how each additional index, on a field containing a random
integer number impacts insert performance. As seen in the _id test above random
indexes are the worst performing compared to sequential or recent-date indexes.

We preload 3 million 4KB documents, then measure loading the next 3 million, and
index N simple integer fields in each. The load batch size is 1,000.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_nindexes"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

### Performance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_nindexes"}},
    {"$set": { "totalKB" : { "$multiply" : [ "$test_config.docSizeKB", "$test_config.totalDocsToInsert"]}}},
  { "$set" : { "opTimeWrites": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "OP_EXECUTION_TIME_WRITES"]}}}}}},
    { "$set" : { "opTimeWrites" : { "$filter": {  "input" : "$opTimeWrites.dataPoints", "cond": { "$ne" : [ "$$this.value",null]}}}}},
 {   "$set" : { "opLatency" : {"$round": { "$avg" : "$opTimeWrites.value"}}}},
    {"$project": {
"opTimeWrites":1,"opLatency":1,
                  "nindexes": "$variant.nSecondaryIndexes","totalKB":1,"totalDocsToInsert":1,
                  "totalMB": {"$round":{ "$divide":  [ "$totalKB",1024]} },
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "MBperSecond": { "$round" : [ {"$divide": ["$totalKB", "$duration"]},2]},
                  "DocsPerSecond" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$test_config.totalDocsToInsert"]}, "$duration"]}]}
    }},
    {"$sort":{ "nindexes": 1}}],
    "columns": ["nindexes", "durationS","totalMB","DocsPerSecond","MBperSecond","opLatency"],
    "headers": ["Number of secondary indexes", "Time Taken (s)","Data Loaded (MB)", "Speed (docs/s)", "Speed (MB/s)","Average Op Latency (ms)"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_nindexes" }},
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
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": { "$divide" : [ "$journalWrite", "$duration"]}}}},
    {"$project": {"nindexes": "$variant.nSecondaryIndexes",
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},"idtype": "$variant.idType",
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
  {"$sort":{ "nindexes": 1}}],

    "columns": ["nindexes","meancpu","iowait","cachePageReadPerSecondKB","compressedDataPerSecondKB","journalPerSecondKB","meanIops","meanWrite","meanRead" ],
    "headers": ["Number of secondary Indexes", "CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","Write from Cache (KB/s)","Write to WAL (KB/s)", "O/S IOPS","O/S Write (MB/s)","O/S Read (MB/s)"]
}
-->  

### Analysis

We can see each additional index adds CPU overhead, and both read and write Disk
operations.

Starting from our baseline write speed of 128MB/s we see adding 4 indexes
reduces write speed by roughly 30% and 8 by 70% whilst 16 indexes result in a
92% performance reduction. The IOPS required rise, although even with 16
indexes, we are still requiring only 2,200 IOPS—less than the minimum provided
on standard disks.

We also see an impact on latency, but we are testing here with batches of 1,000
so a high figure of 3,800ms ultimately translates to only a few 10s of ms of
latency with even 16 inexes when writing individual documents.

### Key Takeaways

* You still need indexes to support all read operations so that determines how
  many you need
* You then need to size for those indexes - having enough RAM for the index
  working set is crucial

## Standard vs. Provisioned IOPS and Throughput

### Description

If a workload is I/O bound, then either the number of write operations or the
total write throughput may be the factor limiting performance. Standard IOPS
have a hard throughput limit as described above in Atlas (AWS), Provisioned
IOPS scale the throughput with the number of IOPS. This test looks at the
implications of Standard vs Provisioned IOPS for read and write performance.

In this test we load 30 million 4KB documents with
4 secondary indexes comparing the implications of Standard(gp3) vs.
Provisioned (io2)  IOPS and different numbers of provisioned IOPS.
We use an M50 to ensure sufficient CPU for the random indexes.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_iops"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

### Performance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_iops"}},
    {"$set": { "totalKB" : { "$multiply" : [ "$test_config.docSizeKB", "$test_config.totalDocsToInsert"]}}},
  { "$set" : { "opTimeWrites": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "OP_EXECUTION_TIME_WRITES"]}}}}}},
    { "$set" : { "opTimeWrites" : { "$filter": {  "input" : "$opTimeWrites.dataPoints", "cond": { "$ne" : [ "$$this.value",null]}}}}},
 {   "$set" : { "opLatency" : {"$round": { "$avg" : "$opTimeWrites.value"}}}},

{
 "$set": { "iosystem": {"$concat": ["$variant.instance.atlasDiskType", " (", { "$toString": "$variant.instance.atlasDiskSizeGB" }, "GB@", { "$toString": "$variant.instance.atlasIOPS" }, "IOPS)" ] } } },
    {"$project": {"opTimeWrites":1,"opLatency":1,
                  "iosystem": 1 ,"totalKB":1,"totalDocsToInsert":1,
                  "totalMB": {"$round":{ "$divide":  [ "$totalKB",1024]} },
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "MBperSecond": { "$round" : [ {"$divide": ["$totalKB", "$duration"]},2]},
                  "DocsPerSecond" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$test_config.totalDocsToInsert"]}, "$duration"]}]}
    }},
    {"$sort":{ "iosystem": 1}}],
    "columns": ["iosystem", "durationS","totalMB","DocsPerSecond","MBperSecond","opLatency"],
    "headers": ["Disk Specification", "Time Taken (s)","Data Loaded (MB)", "Speed (docs/s)", "Speed (MB/s)","Average Op Latency (ms)"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_iops" }},
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
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": { "$divide" : [ "$journalWrite", "$duration"]}}}},
{
 "$set": { "iosystem": {"$concat": ["$variant.instance.atlasDiskType", " (", { "$toString": "$variant.instance.atlasDiskSizeGB" }, "GB@", { "$toString": "$variant.instance.atlasIOPS" }, "IOPS)" ] } } },

    {"$project": {"iosystem":1,
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},"idtype": "$variant.idType",
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
  {"$sort":{ "iosystem": 1}}],

    "columns": ["iosystem","meancpu","iowait","cachePageReadPerSecondKB","compressedDataPerSecondKB","journalPerSecondKB","meanIops","meanWrite","meanRead" ],
    "headers": ["Disk Specification", "CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","Write from Cache (KB/s)","Write to WAL (KB/s)", "O/S IOPS","O/S Write (MB/s)","O/S Read (MB/s)"]
}
-->  

### Analysis

We have created a scenario here where every write has an impact on four random
indexes, the indexes are larger that fit in RAM and so we are seeing a very
significant amount of read-into-cache. Effectively, we need the IOPS to support
reading rather than writing as we have to read index blocks to modify them.

This then allows us to write more so we see higher write IO, but this is due to
having more available for the required reads first.

### Key Takeaways

* IOPS should be though of as for reads where the cache is not large enough to
  hold the index blocks. * This can impact write-mostly workloads too is indexes
  need pulled into the cache.

## Impact of Instance Size on write performance

### Description

This test compares the performance increasing Atlas instance sizes on
performance

in an insert-with-secondary-indexes workload. The test inserts 3M x 4KB
documents with 4 secondary indexes, then measures the time to add 12M additional
documents. As the instance size increases, both the CPU and RAM increase as we
are not
using any low-cpu instances at this time. From M30 to M40 the cache increases
from 2GB to 8GB as the RAM goes from 8GB to 16GB.

In these tests we are using 3000 provisioned IOPS.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_instancesize"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

### Performance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_instancesize"}},
    {"$set": { "totalKB" : { "$multiply" : [ "$test_config.docSizeKB", "$test_config.totalDocsToInsert"]}}},
  { "$set" : { "opTimeWrites": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "OP_EXECUTION_TIME_WRITES"]}}}}}},
    { "$set" : { "opTimeWrites" : { "$filter": {  "input" : "$opTimeWrites.dataPoints", 
                                                  "cond": { "$ne" : [ "$$this.value",null]}}}}},
 {   "$set" : { "opLatency" : {"$round": { "$avg" : "$opTimeWrites.value"}}}},
{ "$set": { "instanceType": "$variant.instance.atlasInstanceType"} },
    {"$project": {"opTimeWrites":1,"opLatency":1,
                  "instanceType": 1 ,"totalKB":1,"totalDocsToInsert":1,
                  "totalMB": {"$round":{ "$divide":  [ "$totalKB",1024]} },
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "MBperSecond": { "$round" : [ {"$divide": ["$totalKB", "$duration"]},2]},
                  "DocsPerSecond" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$test_config.totalDocsToInsert"]}, "$duration"]}]}
    }},
    {"$sort":{ "instanceType": 1}}],
    "columns": ["instanceType", "durationS","totalMB","DocsPerSecond","MBperSecond","opLatency"],
    "headers": ["Instance Type", "Time Taken (s)","Data Loaded (MB)", "Speed (docs/s)", "Speed (MB/s)","Average Op Latency (ms)"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "insert_instancesize" }},
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
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": { "$divide" : [ "$journalWrite", "$duration"]}}}},
{ "$set": { "instanceType": "$variant.instance.atlasInstanceType"} },
    {"$project": {"instanceType":1,
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},"idtype": "$variant.idType",
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
  {"$sort":{ "instanceType": 1}}],

    "columns": ["instanceType","meancpu","iowait","cachePageReadPerSecondKB","compressedDataPerSecondKB","journalPerSecondKB","meanIops","meanWrite","meanRead" ],
    "headers": ["Disk Specification", "CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","Write from Cache (KB/s)","Write to WAL (KB/s)", "O/S IOPS","O/S Write (MB/s)","O/S Read (MB/s)"]
}
-->  

### Analysis

We see the performance of the M30 instance is very low, relatively—this is also
CPU bound at an average of 96% CPU. CPU is being used to compress both
replication streams and the writes to disk, so there is a notable overhead in
any MongoDB system for these system tasks. This is one reason that these tests
are all on an M40 or higher where a couple of cores can be left to handle I/O
and replication.

When we get to an M40 we see no I/O Wait and 88% CPU usage suggesting that the
limiting factor for this instance type is the CPU.

Larger than an M50 we see interesting behaviour as the data written per second
rises, although in no way proportional to the doubling instance sizes
M50->M60->M80 double in RAM and CPU each time we double the instance size we get
a 20-30% increase in throughput.

The I/O Wait observed on M50 upwards points to what out limiting factor here -
on an M50 we have significant read into cache - and we are hitting the ~370MB/s
we get on an Privisioned IOPS drive with 3,000 IOPS.

This also lets us establish that in general we get 125MB/s of write per 1000
IOPS on provisioned drives

### Key Takeaways

* There is a balance between CPU and Disk in terms of what limits write
  throughput.
* Instances over M40 can saturate 3,000 IOPS on when writing to provisioned
  drives.
* Ever-increasing instance sizes are unhelpful if the issue is IO-related
* Replication can saturate one core per replica and bounds replication at
  3-400 MB/s.

## Impact of hot documents and concurrency on write performance

### Description

Where many threads simultaneously modify the same document, the internal
optimistic-with-retry concurrency inside MongoDB means that N simultaneous
updates result in an N^2/2 quantity of work. This test measures the real-world
impact of this retry contention. This is primarily related to RAM and CPU usage,
so we can use a relatively small data set with 1 Million 1KB documents. Then we
update one of them with a varying number of threads, incrementing a single value
so each write does eventually take place.

This test is run with an M30 instance and is inherently CPU-bound. As the number
of
available cores grows, the performance will not significantly improve as we are
essentially measuring a single threaded operation (one update to one document)
and the cost of excessive retries. Additional CPU can handle more wasted write
conflicts but not really improve throughput.

There is a variant to this anti-patttern not shown here where each thread
ultimately updates a different document. In this case the problem is compounded
by the document not matching the query after a failure and requiring a new
query. this has significantly more performance overhead. An example might be
dequeueing tasks using findOneAndUpadte.

We are also looking here at the impact of the update also updating an index
resulting in two wiredtiger tables being modified rather than just one.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "concurrency"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

### Performance

<!-- MONGO_TABLE: 

{
  "collection": "results",
  "pipeline": [
    { "$match": {"_id.testname" : "concurrency"}},
    { "$set" : { "opTimeWrites": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "OP_EXECUTION_TIME_WRITES"]}}}}}},
    { "$set" : { "opTimeWrites" : { "$filter": {  "input" : "$opTimeWrites.dataPoints", 
                                                  "cond": { "$ne" : [ "$$this.value",null]}}}}},
    { "$set" : { "opLatency" : {"$round": [{ "$avg" : "$opTimeWrites.value"},2]}}},
    { "$set" : { "writeConflicts" : {"$subtract" : [ "$after_status.metrics.operation.writeConflicts", "$before_status.metrics.operation.writeConflicts"]}}},
    { "$project": {"opLatency":1, "threads":"$variant.numberOfThreads","index":"$variant.indexUpdate",
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0, "writeConflicts": 1,
                  "DocsPerSecond" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$variant.nUpdates"]}, "$duration"]}]}
     }},
  {"$sort":{ "index": 1,"threads":1}}
    ],
  "columns": ["threads","index", "writeConflicts", "durationS","DocsPerSecond","opLatency"],
  "headers": ["Num Threads", "Updating Index", "writeConflicts", "Time Taken (s)", "Update Speed (docs/s)","Average Op Latency (ms)"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "concurrency" }},
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
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": [ { "$divide" : [ "$journalWrite", "$duration"]},2]}}},

    {"$project": { "threads":"$variant.numberOfThreads","index":"$variant.indexUpdate","journalWrite":1,
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},"idtype": "$variant.idType",
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
  {"$sort":{ "index": 1,"threads":1}}],

    "columns": ["threads","index","meancpu","iowait","cachePageReadPerSecondKB","compressedDataPerSecondKB","journalPerSecondKB","meanIops","meanWrite"],
    "headers": ["Num Threads", "Updating Index", "CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","Write from Cache (KB/s)","Write to WAL (KB/s)", "O/S IOPS","O/S Write (MB/s)"]
}
-->  

### Analysis

In this scenario, each request from the client is updating a single doucment -
in this case having just a single thread would have minimal contention and
retries, but time would be wasted in network round trip time.

Leaving aside very unusual scenarios where multiple updates to one documetn were
sent from the client as part of a single call we are therefore looking for a
balance of threads to retries to determine the maximum performance.

Graphing these figures shows a peak around 70 threads so that shoudl be
considered optimal, although it is, of course, dependent on network latency.
Adding the index update and making each operation take slightly longer seems to
push the acceptable thread count up.

The writeConflicts column shows the number of retries representing CPU work
wasted.

Notably as well - the IOPS here is high relative to the volume written, on this
case. We are performing many individual writes of tiny documents, each of which
is forcing frequent disk flushes of the WAL but not enough to maximize the
volume of data written per flush.

### Key Takeaways

* It is OK to have a single document being updated by multiple threads as long
  as this is outside a transaction, however, the maximum throughput it then
  limited to ~4,000 updates/s with more threads slowing this down.
* In general avoid having hot documents unless they are really not very hot.

## Comparison of using UpdateOne vs. FindOneAndUpdate and upsert vs. explicit insert

### Description

We add 500,000 1KB documents to a collection. Then we perform either a series of
updates or a mix of updates and inserts where inserts are handled either by
upsert or by a second call to insert when not found for update.

We also try using both UpdateOne() and FindOneAndUpdate() to compare
performance. In both cases 500,000 write ops are performed using 30 threads.

### Performance

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "updateapi"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  


<!-- MONGO_TABLE: 

{
  "collection": "results",
  "pipeline": [
    { "$match": {"_id.testname" : "updateapi"}},
    { "$set" : { "opTimeWrites": {"$first": {"$filter": { "input": "$metrics.measurements", "cond": { "$eq": ["$$this.name", "OP_EXECUTION_TIME_WRITES"]}}}}}},
    { "$set" : { "opTimeWrites" : { "$filter": {  "input" : "$opTimeWrites.dataPoints", 
                                                  "cond": { "$ne" : [ "$$this.value",null]}}}}},
    { "$set" : { "opLatency" : {"$round": [{ "$avg" : "$opTimeWrites.value"},2]}}},

    { "$project": {"opLatency":1, "function":"$variant.updateFunction","upsert":"$variant.upsert","percentnew":"$variant.percentNew",
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "DocsPerSecond" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$variant.nUpdates"]}, "$duration"]}]}
     }},
  {"$sort":{ "percentnew": 1,"function":-1,"upsert":1}}
  ],
  "columns": ["percentnew","function","upsert", "durationS","DocsPerSecond","opLatency"],
  "headers": ["Percent Inserts", "Function", "Using Upsert", "Time Taken (s)", "Update Speed (docs/s)","Average Op Latency (ms)"]
}
-->  

### Analysis

In this set of results, we see that FindOneAndUpdate (And its Replace
equivalent) slower than updateOne, this we should expect given it does more, but
many developers fail to appreciate the important difference and use
FindOneAndUpdate rather than UpdateOne because of the somewhat similar naming.

We also learn that adding upsert to any update command makes it slower even
where no inserts are taking place. This is because there is optimisation for the
update-only code path in MDB 8.0 that does not happen currently when upsert is
in the flags.

### Key Takeaways

* If you do not need the document being modified returned, use updateOne not
  findOneAndUpadte
* In cases where the insert branch of upsert is frequent, then upsert can save a
  network call in many cases making it faster overall
* In more typical cases avoid upsert and manually handle the insert case—you
  need to
  handle cases where two threads fallback to insert and one much retry update
  anyway.

## High-level comparison of querying types

### Description

This looks at a high level at various forms of simple indexed querying,
including those with an imperfect index. The test inserts 60 Million 1KB
documents and then runs a series of queries against them. These tests are on an
M40 with 200GB Standard disks.

These are divided into 150,000 groups with the field 'group' having an integer
group id. There are 400 documents in each group. The documents in a group are
not inserted contiguously in the database, as we would not expect them to be in
a real-world collection. As the documents are 1KB in size and there are 150,000
groups we can make a reasonable assumption the no database page (28KB post
compression) will contain two documents from the same group.

Tests run on the whole data set and on a subset to show cached and uncached
results where possible. Tests may fetch a single document, a set from the same
group or a single document from many different groups.

Within the group the field group_seq has a value 0–400 allowing us to fetch a
specific group and sequence number to investigate compound indexing. The field
group_seq_i is the same as group_seq but indexed.

We explicity return the value of a non-existent field to force the server to
fetch and scan the document as a non-covered projection would. This is actually
worst-case as a projection can return as soon as it finds all the fields.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "query_full"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

### Performance

<!-- MONGO_TABLE: 

{
  "collection": "results",
  "pipeline": [
    { "$match": {"_id.testname" : "query_full"}},
{ "$sort" : { "_id.order":1}},
    { "$project": { "comment":"$variant.comment",
"nQueries" : "$testResults.nQueries",
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "qps" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$testResults.nQueries"]}, "$duration"]}]}
     }}

  ],
  "columns": ["comment","durationS","qps"],
  "headers": ["Query Type", "Time Taken (s)", "Speed (Queries/s)"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "query_full" }},
{ "$sort" : { "_id.order":1}},
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

    { "$set" : { "networkBytesOut" : {"$subtract" : [ "$after_status.network.physicalBytesOut", "$before_status.network.physicalBytesOut"]}}},

    { "$set" : { "totalIops" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_IOPS_TOTAL"]}}}}}},
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": [ { "$divide" : [ "$journalWrite", "$duration"]},2]}}},
    { "$set" : { "networkOutPerSecondKB" :{"$round": [ { "$divide" : [ "$networkBytesOut", "$duration"]},2]}}},
    {"$project": { "comment":"$variant.comment", "threads":"$variant.numberOfThreads","index":"$variant.indexUpdate","journalWrite":1,
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},"idtype": "$variant.idType",
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,"networkOutPerSecondKB":1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}}

],

    "columns": ["comment","meancpu","iowait","cachePageReadPerSecondKB","networkOutPerSecondKB","meanIops"],
    "headers": ["Query Type",  "CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)","KB/s Network out", "O/S IOPS"]
}
-->  

### Analysis

Our headline suggests that an M40 instance, when the required data is
indexed and in cache, can achieve ~22,000 queries per second.
This has 70% CPU usage and no IOPS, so it is not immediately obvious
what is limiting this. It does not appear to be network performance.

Not shown here, a previous test with an M30 showed this at ~11,000 so we can
work on the basis that the best performance on simple queries is 5,500 queries
per second per core.

Data not being in the cache drops this by 83% this being on a fast SSD-based
network disk and being presumably constrained by the 3,000 IOPS available.

When we move to a single index lookup but retrieving many documents from the
same simple query, we see our QPS drop to 5,300. In this case each query is
fetching 100 documents, so the overall docs/s is higher, this is partly down to
network round trip overheads making fetching one document queries less
efficient. The first Query is finding 22,000 documents/s the second is finding
more than 500,000 document/s.

Retrieving many documents where none are in the cache is brutally slow at 150
qps; This is a fully indexed query where the FETCH is needing to perform an IO
operation. If each returned document needs its own disk read then our 3,000 IOPS
should allow us only 30 queries per second, we can assume we are therefore
getting some benefit from the OK cache. We are reading 9,200 pages into
database cache per second with only 3,400 IOPS. This is from readahead (Where
the OS reads a bit more of a file than requested and holds it in the OS
filesystem cache) and the size of an IOP (256K).

We can see that when performing paging, a range-based page retrieval using $gt
can be 50% faster than a skip-based page once the number of pages skipped is
around 20. This is despite the fact MongoDB no-longer fetches the intermediate
documents. With only 20 retrieved oer group it is not practical to test this
out-of-cache.

When we identify a single document with a compound index on two fields, we are
seeing 14K queries per second; This is 35% slower than a single field index
lookup, it also uses 10% more CPU, the assumption here is that the cmore complex
query processing is what is slowing it. It is also possible that there are
optimal paths for a query on _id that are not followed when querying by two
other fields.

Conversely - when we query by two terms but only one is indexes we need to read
multiple documents and filter them to find the match. This then ends up with a
query that is not cached, and our performance drops from 10,000 queries per
second to only 22. This shows how critical it is to have the required compound
index.

When the data is cached, retrieving 100 documents with a 100 term $in clause is
about 40% slower than retrieving them with a single query term. This includes
the cost of the fetch. If we can do 22K single lookup single fetch queries per
second and 5,300 single lookup, 100 fetch queries. We can invert this.

```
L + F = 4,5s per 100,000 queries
L + 100F = 18.6s per 100,000 queries

99F = 14.1s per 100,000 queries

100L + 100F = 26.7s per 100,000 queries
100L = 26.7 - 14.1 = 12.6s per 100,000 queries
L = 0.126s per 100,000 queries
L = 800,000 QPS
```

This suggests we can perform about 800,000 lookups per second or that adding
100 to our query adds about 1ms.

### Key Takeaways

* When considering query speed, don't think of queries per second alone, think
  of documents returned per second.
* When reading out of cache throughput is limited by IOPS as random reads are
  required, readahead helps though.
* Imperfectly indexed queries requiring a FILTER the document have a large
  overhead. Especially if not in cache.
* Many (100) terms in a $in clause are almost as fast as a single term.

## Comparing the number of documents retrieved after index lookup

### Description

This test fetches N documents identified by a single indexed key comparing both
cached and uncached options. It projects and returns only a non existant field
as null to the client for each to avoid measuring network constraints.
It needs to FETCH the entire document into cache as the field returned is not in
the index used for retrieval and cannot be covered.

This test shows how the performance is impacted by the number of documents
retrieved. Fetching more than 101 documents further requires a second network
call ( _getMore_) by the client to fetch the next batch of documents. This can
be modified with batchsize if the specific number is known.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "query_fetch"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

### Performance

<!-- MONGO_TABLE: 

{
  "collection": "results",
  "pipeline": [
    { "$match": {"_id.testname" : "query_fetch"}},
    { "$project": { "comment":"$variant.comment",
"nQueries" : "$testResults.nQueries",
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "qps" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$testResults.nQueries"]}, "$duration"]}]}
     }},
{ "$sort" : { "start_time":1}}
  ],
  "columns": ["comment","durationS","qps"],
  "headers": ["Query Type", "Time Taken (s)", "Speed (Queries/s)"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "query_fetch" }},
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
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": [ { "$divide" : [ "$journalWrite", "$duration"]},2]}}},

    {"$project": { "comment":"$variant.comment", "threads":"$variant.numberOfThreads","index":"$variant.indexUpdate","journalWrite":1,
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},"idtype": "$variant.idType",
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
  {"$sort":{ "start_time": 1}}],

    "columns": ["comment","meancpu","iowait","cachePageReadPerSecondKB","meanIops"],
    "headers": ["Query Type",  "CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)", "O/S IOPS"]
}
-->  

### Analysis

What we see here is that cache is not all-or-nothing, when we are using a
portion of our working set, then some caching is happening. For example, the
results for a single document are the same for both as we are actually caching
the first document in every group, so the 1 document, not in cache result is
actually in cache.

As the number of documents per group returned grows, the ability to cache
decreases
to the point where we have fully out-of-cache reads as shown in the table above.

## Impact of IOPS on out-of-cache query performance

### Description

THis test measures the impact available IOPS have on read performance.

### Base Atlas Instance

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "query_fetch_io"}},
    {"$limit":1},
    {"$project": {
"atlasInstanceType":"$bench_config.atlasInstanceType",
 "atlasDiskType": "$bench_config.atlasDiskType",
 "atlasIOPS": "$bench_config.atlasIOPS",
"atlasDiskSizeGB":"$bench_config.atlasDiskSizeGB"
}}
   ],
    "columns": ["atlasInstanceType", "atlasDiskType", "atlasIOPS", "atlasDiskSizeGB"],
    "headers": ["Instance Type", "Disk Type", "Disk IOPS", "Disk Size"]
}
-->  

### Performance

<!-- MONGO_TABLE: 

{
  "collection": "results",
  "pipeline": [
    { "$match": {"_id.testname" : "query_fetch_io"}},
    { "$project": { "cost":{"$divide":["$_id.instance.cost",100]},"disktype":"$variant.instance.atlasDiskType","iops":"$variant.instance.atlasIOPS",
"nQueries" : "$testResults.nQueries",
                  "durationS": {"$round":{"$divide":[ "$duration",1000]}},
                  "_id": 0,
                  "qps" : { "$round" : [ {"$divide": [{"$multiply":[1000,"$testResults.nQueries"]}, "$duration"]}]}
     }},
{ "$sort" : { "disktype":1,"iops":1}}
  ],
  "columns": ["disktype","iops","durationS","qps","cost"],
  "headers": ["Disk Type", "Disk  IOPS", "Time Taken (s)", "Speed (Queries/s)","$ per hour"]
}
-->  

### Resource Usage

<!-- MONGO_TABLE: 
{
  "collection": "results",
  "pipeline": [
    {"$match": {"_id.testname" : "query_fetch_io" }},
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
    { "$set" : { "meanIops" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalIops.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalWrite" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_WRITE"]}}}}}},
    { "$set" : { "meanWrite" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalWrite.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "totalRead" : {"$first": {"$filter": { "input": "$metrics.diskMetrics", "cond": { "$eq": ["$$this.name", "DISK_PARTITION_THROUGHPUT_READ"]}}}}}},
    { "$set" : { "meanRead" : {"$round": { "$avg" : { "$filter" : { "input" : "$totalRead.dataPoints.value", "cond" : {"$ne" :[ "$$this",null]}}} }}}},
    { "$set" : { "cachePageReadPerSecondKB" : {"$round" : { "$divide" : [ "$cacheReadIn", "$durationS"]}}}},
    { "$set" : { "compressedDataPerSecondKB" :{"$round": { "$divide" : [ "$cacheWriteOut", "$duration"]}}}},
    { "$set" : { "journalPerSecondKB" :{"$round": [ { "$divide" : [ "$journalWrite", "$duration"]},2]}}},

    {"$project": {  "disktype":"$variant.instance.atlasDiskType","iops":"$variant.instance.atlasIOPS", "threads":"$variant.numberOfThreads","index":"$variant.indexUpdate","journalWrite":1,
                 "userCPU":1,"meanIops":1,"meanWrite":{"$round":{"$divide":["$meanWrite",1048576]}},"meanRead":{"$round":{"$divide":["$meanRead",1048576]}},"idtype": "$variant.idType",
                "docSizeKB": "$variant.docSizeKB","cacheWriteOut":1,"journalPerSecondKB" :1,"journalWrite":1,
                "cachePageReadPerSecondKB":1,"compressedDataPerSecondKB" :1,
                "cacheReadInMB" : { "$floor": { "$divide": [ "$cacheReadIn",1048576 ] }},
                "meancpu": {"$round":{ "$avg" : "$cpuReadings"}}, "iowait" :{"$round": { "$avg" : "$iowaitCPU.dataPoints.value"}}
        }
    },
    { "$set" : { "estimatedIOPS" : { "$round": { "$add" : [ "$cachePageReadPerSecondKB", { "$divide" : ["$journalPerSecondKB",256]},
  { "$divide" : ["$compressedDataPerSecondKB",256]}]}}}},
  { "$sort" : { "disktype":1,"iops":1}}],

    "columns": ["disktype","iops","meancpu","iowait","cachePageReadPerSecondKB","meanIops"],
    "headers": ["Disk Type", "Disk IOPS", "CPU Usage (%)", "Time waiting for I/O (%)","Read into Cache (Pages/s)", "O/S IOPS"]
}
-->  

### Analysis

Retrieval speed appears to be superlinear with IOPS, a 50% increase in
IOPS results in a doubling of the query speed, a 100% increase in IOPS results
in a quadrupling of the query speed. Conversely, a 66% decrease in IOPS resulted
in an 85% decrease in query speed. This warrants further investigation.

What we do see is reduced IO wait times and increased CPU usage, but it is
unexpected for these to be superlinear.

## To Add

* Ingesting data
    * ~~Document size~~
    * ~~Batching vis Single Insert~~
    * ~~ObjectId vs BusinessID vs UUID~~
    * ~~number of indexes and cache~~
    * ~~Iops (Provisioned vi Standard)~~
    * ~~Instance sizes~~

* Reading Data
    * ~~Retrieval single By Key~~
    * ~~Retrieval set By Single Key~~
    * ~~Retrieval page N with skip~~
    * ~~Retrieval next page with range quey~~
    * ~~Retrieval part index~~
    * ~~Retrieval $in~~
    * ~~Retrieval out of cache~~
* Modifying Data
    * Replace
    * Replace and cache
    * Updates
    * Replace
    * Update
    * Impacted indexes
* Aggregation
    * Aggregation group
    * Aggregation ¢lookup
* Deletion