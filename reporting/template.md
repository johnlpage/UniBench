# 🚧🚧 UNDER CONSTRUCTION 🚧🚧

Ignore content for now.

# MongoDB Performance Tables

| Author      | John Page  |
|-------------|------------|
| **Date**    | 2020-09-22 |
| **Version** | 0.1        |

## About this document

This document shows the expected performance of MongoDB when performing a given task. Each table shows the impact
on performance when changing parameters. For example, adding an index. This is testing only Database performance, you
should assume that the client is using MongoDB optimally and that there are no network constraints between client and
server.

Unless otherwise specified, the database tested is a 3-node Replica Set in MongoDB Atlas using an M30 (2 x vCPU, 8GB
RAM, 2GB configured as
Cache). This is using default of 3,000 IOPS on Amazon Web Services. Writes are using write concern majority, all reads
are from the primary.

The intent of this document is to assist in understanding the approximate expected performance of MongoDB. This
information can be used either to verify that your own application running on MongoDB is performing as expected or to
help you make decisions about how to configure your MongoDB instance for a given performance target.

It is not possible to document every possible combination of operations or how a mix of operations will interact,
however, this data should allow you to infer that. Notes will be supplied with each test where the results show
something of significance.

The performance of MongoDB or any database will depend on the available CPU and Disk I/O capability.
Available RAM will further reduce the amount of Disk I/O required by caching and amortizing write requests where safe to
do so.
This assumes an
ideal client as used in most of these cases, however, some client constructs and then require more work per write
operation by the server, which is demonstrated in later examples.

The author has tried to include explanations for the results and the underlying low-level behavior that makes them so
with each example.

# Data Ingestion

This section covers getting data from outside MongoDB into MongoDB. Either data known to be new or data where some
records are new, doem are modified and others are identical to existing records, existing in this case being based on a
known key field.

## Impact of document size on insert speed

This shows how the document size impacts the speed in MB/s when using `insert` operations to add documents and assign
them a primary key. In the test 2GB of data was bulk inserted into an empty
collection. The only index is the _id index with the default ObjectID(), in this was only a small set of database blocks
are being written to at any one time, so nearly all writes to the database are appended with minimal random I/O and
maximal use of IOPS.

Data like this is very quick to write, but without additional indexes it can only be efficiently retrieved using a
single kay and is usually only useful for logging use cases.



<!-- MONGO_TABLE: {  
"collection": "results",
    "pipeline": [
    {"$match": {"test_config.filename" : "insert_docsize"}},
    {"$set": { "durationMillis" : { "$subtract" : [ "$end_time", "$start_time" ]}}},
    {"$set": { "totalKB" : { "$multiply" : [ "$variant.docSizeKB", "$variant.totalDocsToInsert"]}}},
    {"$project": {"Kilobytes": "$variant.docSizeKB", "totalKB": 1, "durationMillis": 1, "_id": 0, "MBs": {"$divide": ["$totalKB", "$duration"]}}},
    {"$sort":{ "Kilobytes": 1}}],
    "headers": ["Kilobytes", "MBs", "durationMillis","totalKB"]
} -->  

## To Add

* Ingesting data
* Document size
* ObjectId vs BusinessID is UUID
* Batching vis Single Insert
* NUM index and cache
* Index type
* Replace
* Replace and cache
* Iops
* Updates
* Replace
* Update
* Impacted indexes
* Instance sizes
* Retrieval single
* Retrieval set
* Retrieval page N
* Retrieval next page
* Retrieval part index
* Retrieval $in
* Retrieval put of cache
* Aggregation group
* Aggregation ¢lookup
* Deletion