# 🚧🚧 UNDER CONSTRUCTION 🚧🚧

Ignore content for now.

# MongoDB Performance Tables

| Author      | John Page  |
|-------------|------------|
| **Date**    | 2020-09-22 |
| **Version** | 0.1        |

## TLDR;

This document shows the performance you can expect from MongoDB on a given
hardware infrastructure. You can use it to compare the performance of your own
client code and to determine the hardware required or a given performance
target. It highlights the impact of various parameters on the performance of
MongoDB.

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

As expected - the more random a value the slower it is to write as the index
grows larger than RAM. What is not show here is that the UUID becomes ever
slower over time as the index grows, the Business ID will stabilise.

The Metrics returned for Write from cache are considerably higher than expected
and to not tally with the bytes written to the disk by the OS, this is an
indicaiton that this metric is not a direct indication of bytes flushed to disk,
unlike the write to WAL metric.

The Business ID seems to require about 65% of the reads into cache that the UUID
does, but as expected far fewer blocks are dirtied when writing. With 20,000 '
accounts'
and only 24 million records, given the even distribution, it's possible that hat
there are still no 'cold' blocks in the cache.

## Impact of number of indexes on write speed

### Description

This test shows how each additional index, on a field containing a random
integer
number impacts insert performance. As seen in the _id test abouve random indexes
are
the worst performing compated to sequential or recent-date indexes which are the
best performing.

We preload 3M x 4KB document then measure loading the next 3M , and index N
simple integer fields in each. The load batch size is 1,000.

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

## Standard vs Provisioned IOPS and Throughput

### Description

Where a workload is I/O bound, then boht the number of write operations and the
total disk bandwidth are the factor limiting performance. Standard IOPS have a
hard throughput limit of 125MB/s in Atlas (AWS), Provisioned IOPS scale the
throughput with the number of IOPS. This test looks at the implications
ofStandard vs Provisioned IOPS for read and write performance.

In this test We preload 3M x 4KB document then measure loading the next 3M with
4 secondary indexes comparing the implications of Standard(gp3) vs.
Provisioned (io2) classs IOPS and different numbers of IOPS. By default, all
these tests are run on an M30 AWS instance. On an M30 the maximum number of
provisioned IOPS is 3,000 so this tests 3,000 standard IOPS, 3,000 provisioned
IOPS and 6,000 standard IOPS achieved by increasing the disk size from 60GB to
2TB.

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

## Impact of Instance Size on write performance

### Description

This test compares the performance increasing Atlas instance sizes on
performance
in an insert-with-secondary-indexes workload. The test inserts 3M x 4KB
documents with 4 secondary indexes, then measures the time to add 3M documents.
As the instance size increases, both the CPU and RAM increase as we are not
using any low-cpu instances at this time. From M30 to M40 the cache increases
from 2GB to 8GB as the RAM goes from 8GB to 16GB.

In these tests we are using 2000 provisioned IOPS.

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

## Impact of hot documents and concurrency on write performance

### Description

Where many threads simultaneously modify the same document - the internal
optimistic-with-retry
concurrency in MongoDB means that N simultanous updates result in N^2/2 quantity
of work; this test measures the
real world impact of this. This is primarily related to RAM and CPU usage so we
can use a relatively small data set
with 1 Million 1KB documents. We are only modifying a small number of them
anyway.
We first insert 1 million 1KB documents, then update one of them with a varying
number of threads, per perform

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

## Comparison of using UpdateOne vs. FindOneAnd Update and upsert vs. insert

### Description

We add 500,000 1KB documents to a collection. Then we perform either a series of
updates or a mix of updates and
inserts where inserts are handled either by upsert or by a second call to insert
when not found for update.

We also try using both UpdateOne() and FindOneAndUpdate() to compare
performance. In both cases 500,000 write ops are performed using 30 threads.

### Performance

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

## High level comparison of querying types

### Description

This looks at a high level at various forms of simple indexed querying including
those with an imperfect index. The test inserts 5,000,000 4KB Documents, These
are divided into 12,500 groups with the field 'group' having an integer group
id. There are 400 documents in each group. The documents in a group are not
contiguous in the database, as we would not expect them to be in an unclustered
collection.

Tests run on the whole data set and on a subset to show cached and uncached
results. Tests may fetch a single document, a set from the saem group or a
single document from many different groups.

Within the group the field group_seq has a value 0–400 allowing us to fetch a
specific group and sequence number to investigate compound indexing. The field
group_seq_i is the same as group_seq but indexed.

We only retrieve the _id for each document to avoid measuring network cost or
being limited by it.

### Performance

<!-- MONGO_TABLE: 

{
  "collection": "results",
  "pipeline": [
    { "$match": {"_id.testname" : "query_full"}},
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
    {"$match": {"_id.testname" : "query_full" }},
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

## Comparing the number of documents retrieved after and index lookup

### Description

This test fetches N documents identified by a single indexed key comparing both
cached and uncached options. Although it projects and returns only the _id
field to the client for each to avoid measuring network, it needs to FETCH the
entire document into cache to do so as _id is not in the index used.

This shows how the performance is impaced by the number of documents. Fetching >
101 documents further requires a second call _getmore_ by the client to fetch
the next batch of documents.

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

We can see that the impact of fetching up to 100 documents where the data is in
cache is minimal, the FETCH operation is relatively inexpensive. Beyond 100,
then the second network call seems to have an outsize impact on performance with
speed dropping by 40% at 200 and again by 40% when we go to 400 despite these
being in the same call tp getmore.

Outside of cache then the cost of each FETCH is much more significant, although
this is likely dependent on the Disk IOPS available.

## # Impact of IOPS on out-of-cache query performance

### Description

Sometimes the data volume of the working set is unavoidably large, and the
working set cannot be held in cache. This examines the impact of the selected IO
subsystem on the performance of the workload. A before we are using an M40
instance in increasing the data volumnes for this test as an M30 does not allow
the full range of IO Options.

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
* Replacing Data~~
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