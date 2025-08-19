# 🚧🚧 UNDER CONSTRUCTION 🚧🚧

Ignore content for now.

# MongoDB Performance Tables

| Author      | John Page  |
|-------------|------------|
| **Date**    | 2020-09-22 |
| **Version** | 0.1        |

## About this document

This document shows the expected performance of MongoDB when performing a given
task. Each table shows the impacton performance when changing parameters. For
example, adding an index. This is testing only Database performance, you should
assume that the client is using MongoDB optimally and that there are no network
constraints between client and server.

Unless otherwise specified, the database tested is a 3-node Replica Set in
MongoDB Atlas using an M30 (2 x vCPU, 8GB RAM, 2GB configured as Cache). This is
using default of 3,000 IOPS on Amazon Web Services. Writes are using write
concern majority, all reads
are from the primary.

In MongoDB, you can scale vertically by using larger hardware but also
horizontally by adding more replica sets, many workloads will scale linearly to
1000's of times the throughput shown here.

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
Disk I/O capability. Available RAM will further reduce the amount of Disk I/O
required by caching and amortizing write requests where safe to do so. This
assumes an ideal client as used in most of these cases, however, some client
constructs and then require more work per write operation by the server, which
is demonstrated in later examples.

The author has tried to include explanations for the results and the underlying
low-level behavior that makes them so with each example.

# Data Ingestion

This section covers getting data from outside MongoDB into MongoDB. Either data
known to be new or data where some records are new, doem are modified and others
are identical to existing records, existing in this case being based on a known
key field.

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
logging use cases.

### Performance

| Document Size (KB) | Time Taken (s)  | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) |
| --: | --: | --: | --: | --: |
| 1 | 448 | 24576 | 56115 | 56.12 |
| 4 | 374 | 24576 | 16808 | 67.23 |
| 32 | 363 | 24576 | 2169 | 69.4 |
| 256 | 333 | 24576 | 295 | 75.52 |
| 2048 | 321 | 24576 | 38 | 78.49 |
  

### Resource Usage

| Document Size (KB) | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | Predicted IOPS | Actual mean IOPS |
| --: | --: | --: | --: | --: | --: | --: | --: |
| 1 | 72 | 4 | 39 | 72761 | 34643 | 459 | 466 |
| 4 | 64 | 8 | 37 | 84520 | 39824 | 523 | 536 |
| 32 | 64 | 9 | 42 | 83136 | 39721 | 522 | 535 |
| 256 | 62 | 11 | 32 | 85306 | 41978 | 529 | 556 |
| 2048 | 60 | 15 | 10 | 86993 | 42083 | 514 | 559 |
  

### Analysis

With small document, there are considerably more index entries for the primary
key which we can assume adds some overhead even when they are essentially
sequential. The default volumens used for these tests are AWS GP3 which have
3,000 IOPS and 125MIB/S write speed. As each inserted document neerds to be
inserted in the Oplog, the Write-ahead-log and the collection, even allowing for
compression this is likely limited by IOPS.

## Impact of client write batch size on write speed

### Description

MongoDB allows you to send multiple write operations to the database in a single
network request. When using simply insertOne(), replaceOne() or save() in Spring
Data then each document is sent seperately. This not only incurrs a network
round trip per document but also needs each document to independantly
wait for durability which means a network roundtript to a secondary and awaiting
a periodic disk flush on the secondary and primary.

When you send multiple write operations in a batch then the ost of the network
and disk flushes are shared between all the documents, although there can be a
little overhead for a larger batvh whilst waiting for the whole batch to be
processed this still results in much faster outcomes.

When processing single wtrites you can use more threads / asyncronous writes to
increase concurrency but it is still far less efficient.

In this test we insert 2GB of data ( 512,000 4KB documents ) using differening
network write batch size to illustrate the impact of not correctly batching
writes for ingestion.

### Performance

| Write Batch Size | Time Taken (s)  | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) |
| --: | --: | --: | --: | --: |
| 1 | 1594 | 24576 | 3947 | 15.79 |
| 10 | 536 | 24576 | 11742 | 46.97 |
| 100 | 407 | 24576 | 15456 | 61.83 |
| 1000 | 354 | 24576 | 17759 | 71.04 |
| 2000 | 356 | 24576 | 17695 | 70.78 |
  

### Resource Usage

| Write Batch Size | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | Predicted IOPS | Actual mean IOPS |
| --: | --: | --: | --: | --: | --: | --: | --: |
| 1 | 71 | 6 | 52 | 21753 | 11071 | 180 | 618 |
| 10 | 66 | 8 | 107 | 61113 | 28274 | 456 | 548 |
| 100 | 67 | 5 | 78 | 78430 | 36658 | 528 | 514 |
| 1000 | 70 | 7 | 210 | 89599 | 42078 | 724 | 571 |
| 2000 | 73 | 5 | 278 | 89036 | 41925 | 790 | 569 |
  

## To Add

* Ingesting data
* Document size
* Batching vis Single Insert
* ObjectId vs BusinessID is UUID
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