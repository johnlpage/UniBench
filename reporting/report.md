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
| 1 | 774 | 24576 | 32503 | 32.5 |

```
[
  {
    "docSizeKB": 1,
    "totalMB": 24576,
    "durationS": 774,
    "MBperSecond": 32.5,
    "DocsPerSecond": 32503
  }
]
```
  

### Resource Usage

| Document Size (KB) | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | Predicted IOPS | Actual mean IOPS |
| --: | --: | --: | --: | --: | --: | --: | --: |
| 1 | 46 | 7 | 12 | 42191 | 20066 | 255 | 276 |

```
[
  {
    "_id": {
      "docSizeKB": 1,
      "testname": "insert_docsize_small",
      "totalDocsToInsert": 25165824,
      "writeBatchSize": 4000
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T10:56:07Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T10:57:06Z",
          "value": 43.57141647329822
        },
        {
          "timestamp": "2025-08-19T10:58:06Z",
          "value": 44.752752285937945
        },
        {
          "timestamp": "2025-08-19T10:59:06Z",
          "value": 43.675175250178995
        },
        {
          "timestamp": "2025-08-19T11:00:06Z",
          "value": 50.18903119431075
        },
        {
          "timestamp": "2025-08-19T11:01:06Z",
          "value": 43.24360341151387
        },
        {
          "timestamp": "2025-08-19T11:02:06Z",
          "value": 41.291891531747616
        },
        {
          "timestamp": "2025-08-19T11:03:06Z",
          "value": 41.85752119526292
        },
        {
          "timestamp": "2025-08-19T11:04:06Z",
          "value": 41.38568360057945
        },
        {
          "timestamp": "2025-08-19T11:05:06Z",
          "value": 42.34408315565032
        },
        {
          "timestamp": "2025-08-19T11:06:06Z",
          "value": 43.622372497418304
        },
        {
          "timestamp": "2025-08-19T11:07:07Z",
          "value": 42.88736030379241
        },
        {
          "timestamp": "2025-08-19T11:08:07Z",
          "value": 42.73127019554282
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 32667398144,
    "journalWrite": 15536429989,
    "meanIops": 276,
    "cachePageReadPerSecondKB": 12,
    "compressedDataPerSecondKB": 42191,
    "journalPerSecondKB": 20066,
    "docSizeKB": 1,
    "cacheReadInMB": 0,
    "meancpu": 46,
    "iowait": 7,
    "estimatedIOPS": 255
  }
]
```
  

### Analysis

With small document, there are considerably more index entries for the primary
key which we can assume adds some overhead even when they are essentially
sequential. The default volumens used for these tests are AWS GP3 which have
3,000 IOPS and 125MIB/S write speed. As each inserted document neerds to be
inserted in the Oplog, the Write-ahead-log and the collection, even allowing for
compression this is likely limited by IOPS.

## Impact of client write batch size on write speed

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