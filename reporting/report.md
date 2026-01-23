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

* [MongoDB Performance Tables](#mongodb-performance-tables)
    * [TLDR;](#tldr)
    * [Table of Contents](#table-of-contents)
    * [Introduction](#introduction)
* [Data Ingestion](#data-ingestion)
    * [Expected insert speed by document size](#expected-insert-speed-by-document-size)
        * [Description](#description)
        * [Base Atlas Instance](#base-atlas-instance)
        * [Performance](#performance)
        * [Resource Usage](#resource-usage)
        * [Analysis](#analysis)
        * [Key Takeaways](#key-takeaways)
    * [Impact of client write batch size on write speed](#impact-of-client-write-batch-size-on-write-speed)
        * [Description](#description-1)
        * [Base Atlas Instance](#base-atlas-instance-1)
        * [Performance](#performance-1)
        * [Resource Usage](#resource-usage-1)
        * [Analysis](#analysis-1)
        * [Key Takeaways](#key-takeaways-1)
    * [Impact of primary key type on write speed](#impact-of-primary-key-type-on-write-speed)
        * [Description](#description-2)
        * [Base Atlas Instance](#base-atlas-instance-2)
        * [Performance](#performance-2)
        * [Resource Usage](#resource-usage-2)
        * [Analysis](#analysis-2)
        * [Key Takeaways](#key-takeaways-2)
    * [Impact of number of indexes on write speed](#impact-of-number-of-indexes-on-write-speed)
        * [Description](#description-3)
        * [Base Atlas Instance](#base-atlas-instance-3)
        * [Performance](#performance-3)
        * [Resource Usage](#resource-usage-3)
        * [Analysis](#analysis-3)
        * [Key Takeaways](#key-takeaways-3)
    * [Standard vs. Provisioned IOPS and Throughput](#standard-vs-provisioned-iops-and-throughput)
        * [Description](#description-4)
        * [Base Atlas Instance](#base-atlas-instance-4)
        * [Performance](#performance-4)
        * [Resource Usage](#resource-usage-4)
        * [Analysis](#analysis-4)
        * [Key Takeaways](#key-takeaways-4)
    * [Impact of Instance Size on write performance](#impact-of-instance-size-on-write-performance)
        * [Description](#description-5)
        * [Base Atlas Instance](#base-atlas-instance-5)
        * [Performance](#performance-5)
        * [Resource Usage](#resource-usage-5)
        * [Analysis](#analysis-5)
        * [Key Takeaways](#key-takeaways-5)
    * [Impact of hot documents and concurrency on write performance](#impact-of-hot-documents-and-concurrency-on-write-performance)
        * [Description](#description-6)
        * [Base Atlas Instance](#base-atlas-instance-6)
        * [Performance](#performance-6)
        * [Resource Usage](#resource-usage-6)
        * [Analysis](#analysis-6)
        * [Key Takeaways](#key-takeaways-6)
* [Comparison of complexity of updates and schema validation](#comparison-of-complexity-of-updates-and-schema-validation)
    * [Description](#description-7)
    * [Base Atlas Instance](#base-atlas-instance-7)
    * [Performance](#performance-7)
    * [Resource Usage](#resource-usage-7)
    * [Analysis](#analysis-7)
    * [Key Takeaways](#key-takeaways-7)
    * [Comparison of using UpdateOne vs. FindOneAndUpdate and upsert vs. explicit insert](#comparison-of-using-updateone-vs-findoneandupdate-and-upsert-vs-explicit-insert)
        * [Description](#description-8)
        * [Performance](#performance-8)
        * [Base Atlas Instance](#base-atlas-instance-8)
        * [Analysis](#analysis-8)
        * [Key Takeaways](#key-takeaways-8)
    * [High-level comparison of querying types](#high-level-comparison-of-querying-types)
        * [Description](#description-9)
        * [Base Atlas Instance](#base-atlas-instance-9)
        * [Performance](#performance-9)
        * [Resource Usage](#resource-usage-8)
        * [Analysis](#analysis-9)
        * [Key Takeaways](#key-takeaways-9)
    * [Comparing the number of documents retrieved after index lookup](#comparing-the-number-of-documents-retrieved-after-index-lookup)
        * [Description](#description-10)
        * [Base Atlas Instance](#base-atlas-instance-10)
        * [Performance](#performance-10)
        * [Resource Usage](#resource-usage-9)
        * [Analysis](#analysis-10)
    * [Impact of IOPS on out-of-cache query performance](#impact-of-iops-on-out-of-cache-query-performance)
        * [Description](#description-11)
        * [Base Atlas Instance](#base-atlas-instance-11)
        * [Performance](#performance-11)
        * [Resource Usage](#resource-usage-10)
        * [Analysis](#analysis-11)

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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M40 | STANDARD | 3000 | 200 |
  

### Performance

| Document Size (KB) | Time Taken (s)  | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) |
| --: | --: | --: | --: | --: |
| 1 | 238 | 24576 | 105842 | 105.84 |
| 4 | 202 | 24576 | 31098 | 124.39 |
| 32 | 190 | 24576 | 4148 | 132.74 |
| 256 | 180 | 24576 | 546 | 139.8 |
| 2048 | 171 | 24576 | 72 | 147.5 |
  

### Resource Usage

| Document Size (KB) | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 1 | 57 | 6 | 11 | 136104 | 65341 | 861 | 192 | 0 |
| 4 | 48 | 6 | 18 | 155925 | 73681 | 1006 | 221 | 0 |
| 32 | 46 | 8 | 21 | 158166 | 75969 | 1046 | 224 | 1 |
| 256 | 35 | 9 | 20 | 157333 | 77706 | 1080 | 223 | 0 |
| 2048 | 34 | 14 | 6 | 160693 | 79080 | 1078 | 224 | 1 |
  

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
125MB/s for any drive <170GB at which point you get an additional 85MB/s.
This then goes up at 5MB/s per additional 10GB up to 260GB. Thereafter, rising
at 1MB/s per additional 10GB.

Our 200GB volume has a maximum write throughput of 125 + 85M + 15 = 225MB/s and
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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M40 | STANDARD | 3000 | 200 |
  

### Performance

| Write Batch Size | Time Taken (s)  | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| 1 | 1038 | 24576 | 6063 | 24.25 | 6 |
| 10 | 292 | 24576 | 21583 | 86.33 | 15 |
| 100 | 235 | 24576 | 26816 | 107.27 | 33 |
| 1000 | 194 | 24576 | 32424 | 129.7 | 747 |
| 2000 | 199 | 24576 | 31667 | 126.67 | 1874 |
  

### Resource Usage

| Write batch size | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 1 | 70 | 4 | 44 | 32868 | 17006 | 1066 | 49 | 0 |
| 10 | 58 | 6 | 87 | 110226 | 51971 | 1012 | 155 | 0 |
| 100 | 46 | 5 | 66 | 134799 | 63601 | 978 | 194 | 0 |
| 1000 | 47 | 14 | 304 | 161111 | 76825 | 1112 | 223 | 2 |
| 2000 | 43 | 19 | 184 | 159314 | 75028 | 1089 | 222 | 3 |
  

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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M40 | STANDARD | 3000 | 200 |
  

### Performance

| Primary Key format | Time Taken (s) | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| BUSINESS_ID | 505 | 41016 | 83205 | 83.21 | 223 |
| OBJECT_ID | 421 | 41016 | 99764 | 99.76 | 171 |
| UUID | 1017 | 41016 | 41298 | 41.3 | 476 |
  

### Resource Usage

| Primary Key format | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| BUSINESS_ID | 81 | 1 | 3942 | 163541 | 50964 | 2589 | 183 | 1 |
| OBJECT_ID | 60 | 4 | 24 | 126911 | 60718 | 918 | 180 | 1 |
| UUID | 83 | 3 | 13439 | 246706 | 25597 | 3154 | 221 | 0 |
  

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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M40 | STANDARD | 3000 | 200 |
  

### Performance

| Number of secondary indexes | Time Taken (s) | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| 0 | 103 | 12857 | 31837 | 127.35 |  |
| 1 | 123 | 12857 | 26652 | 106.61 |  |
| 2 | 131 | 12857 | 25106 | 100.42 | 305 |
| 3 | 143 | 12857 | 22955 | 91.82 | 318 |
| 4 | 152 | 12857 | 21631 | 86.52 | 352 |
| 8 | 346 | 12857 | 9513 | 38.05 | 882 |
| 16 | 1247 | 12857 | 2640 | 10.56 | 3843 |
  

### Resource Usage

| Number of secondary Indexes | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 0 | 0 |  | 16 | 159326 | 75432 |  |  |  |
| 1 | 0 |  | 1189 | 209107 | 63148 |  |  |  |
| 2 | 45 | 2 | 4678 | 229596 | 59484 | 1153 | 208 | 1 |
| 3 | 47 | 1 | 6487 | 216291 | 54389 | 1472 | 212 | 0 |
| 4 | 47 | 1 | 7281 | 211487 | 51251 | 1885 | 213 | 1 |
| 8 | 72 | 1 | 10479 | 197244 | 22540 | 1784 | 145 | 0 |
| 16 | 91 | 0 | 11900 | 181037 | 6254 | 2229 | 136 | 0 |
  

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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M50 | STANDARD | 3000 | 200 |
  

### Performance

| Disk Specification | Time Taken (s) | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| PROVISIONED (200GB@1500IOPS) | 1756 | 117188 | 17088 | 68.35 | 1331 |
| PROVISIONED (200GB@3000IOPS) | 954 | 117188 | 31439 | 125.76 | 655 |
| PROVISIONED (200GB@4500IOPS) | 761 | 117188 | 39396 | 157.58 | 426 |
| PROVISIONED (200GB@6000IOPS) | 745 | 117188 | 40281 | 161.12 | 409 |
| STANDARD (200GB@3000IOPS) | 1328 | 117188 | 22587 | 90.35 | 808 |
  

### Resource Usage

| Disk Specification | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| PROVISIONED (200GB@1500IOPS) | 38 | 21 | 3786 | 160259 | 40487 | 1830 | 164 | 0 |
| PROVISIONED (200GB@3000IOPS) | 65 | 8 | 8904 | 284848 | 74491 | 3939 | 314 | 7 |
| PROVISIONED (200GB@4500IOPS) | 80 | 2 | 10286 | 337191 | 93343 | 5204 | 389 | 1 |
| PROVISIONED (200GB@6000IOPS) | 82 | 1 | 10054 | 339270 | 95441 | 5713 | 402 | 1 |
| STANDARD (200GB@3000IOPS) | 46 | 17 | 5996 | 205160 | 53517 | 2784 | 216 | 0 |
  

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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M30 | PROVISIONED | 3000 | 512 |
  

### Performance

| Instance Type | Time Taken (s) | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| M30 | 4888 | 46875 | 2455 | 9.82 | 10113 |
| M40 | 698 | 46875 | 17181 | 68.72 | 1325 |
| M50 | 326 | 46875 | 36831 | 147.33 | 469 |
| M60 | 282 | 46875 | 42516 | 170.07 | 348 |
| M80 | 199 | 46875 | 60153 | 240.61 | 218 |
  

### Resource Usage

| Disk Specification | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| M30 | 96 | 0 | 5470 | 90152 | 5817 | 453 | 50 | 0 |
| M40 | 88 | 0 | 8388 | 200017 | 40709 | 2674 | 201 | 0 |
| M50 | 52 | 10 | 8012 | 295582 | 87268 | 4582 | 323 | 9 |
| M60 | 29 | 7 | 7568 | 388929 | 100738 | 2794 | 373 | 4 |
| M80 | 7 | 10 | 4526 | 379435 | 142527 | 3736 | 354 | 0 |
  

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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M30 | STANDARD | 3000 | 60 |
  

### Performance

| Num Threads | Updating Index | writeConflicts | Time Taken (s) | Update Speed (docs/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| 10 |  | 525494 | 406 | 2465 | 2.75 |
| 20 |  | 804183 | 298 | 3354 | 4.28 |
| 50 |  | 1048864 | 243 | 4121 | 9.3 |
| 100 |  | 1612936 | 248 | 4035 | 19.58 |
| 200 |  | 3601204 | 334 | 2994 | 53.02 |
| 400 |  | 5425114 | 419 | 2388 | 93.08 |
| 10 | true | 587926 | 445 | 2250 | 3.05 |
| 20 | true | 846006 | 316 | 3160 | 4.49 |
| 50 | true | 2004081 | 293 | 3409 | 12.26 |
| 100 | true | 2369256 | 285 | 3508 | 24.47 |
| 200 | true | 5472133 | 423 | 2362 | 63.15 |
| 400 | true | 6542750 | 471 | 2124 | 101.81 |
  

### Resource Usage

| Num Threads | Updating Index | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 10 |  | 63 | 5 | 2 | 359 | 0.06 | 1068 | 5 |
| 20 |  | 66 | 2 | 3 | 518 | 0.04 | 1063 | 5 |
| 50 |  | 69 | 1 | 6 | 698 | 0.04 | 652 | 4 |
| 100 |  | 75 | 1 | 10 | 785 | 0.04 | 352 | 3 |
| 200 |  | 78 | 0 | 15 | 786 | 0.04 | 142 | 2 |
| 400 |  | 84 | 0 | 27 | 923 | 0.04 | 86 | 2 |
| 10 | true | 78 | 2 | 3 | 482 | 0.07 | 1027 | 5 |
| 20 | true | 74 | 1 | 2 | 598 | 0.05 | 1025 | 5 |
| 50 | true | 73 | 1 | 6 | 693 | 0.04 | 689 | 4 |
| 100 | true | 75 | 1 | 13 | 938 | 0.05 | 350 | 3 |
| 200 | true | 84 | 0 | 14 | 774 | 0.04 | 139 | 2 |
| 400 | true | 86 | 0 | 23 | 936 | 0.06 | 82 | 2 |
  

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

## Comparison of update complexity and schema validation

### Description

Using UpdateOne() we update 4KB document both in and out of cache incrementing
different numbers of fields with _$inc_. We also use an update with $expr to perform
the update on 50 fields and we perform a $inc update on 50 fields with scheam validation.

### Base Atlas Instance

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M40 | STANDARD | 3000 | 200 |
  

### Performance

| Description | Time Taken (s) | TotalUpdates | Update Speed (docs/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: |
| 1 document, cached, 1 field | 901 | 4177328 | 4637 | 5.38 |
| 1 document, cached, 10 fields | 901 | 3873151 | 4300 | 5.9 |
| 1 document, cached, 20 fields | 901 | 3685058 | 4091 | 6.28 |
| 1 document, cached, 50 fields | 901 | 3331796 | 3699 | 6.89 |
| 1 document, cached, 50 fields, $expr | 901 | 2886748 | 3205 | 7.98 |
| 1 document, cached, 50 fields, validation | 901 | 3335410 | 3703 | 6.96 |
| 1 document, uncached, 1 field | 901 | 1142058 | 1268 | 24.21 |
  

### Resource Usage

| Description | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: |
| 1 document, cached, 1 field | 84 | 2 | 3955 | 170454 | 0.04 | 4446 | 144 |
| 1 document, cached, 10 fields | 82 | 3 | 3787 | 152938 | 0.04 | 4542 | 128 |
| 1 document, cached, 20 fields | 81 | 4 | 3753 | 146727 | 2122.15 | 4469 | 124 |
| 1 document, cached, 50 fields | 82 | 3 | 3496 | 134415 | 2807.79 | 4282 | 116 |
| 1 document, cached, 50 fields, $expr | 84 | 3 | 3335 | 113263 | 2417.95 | 4018 | 99 |
| 1 document, cached, 50 fields, validation | 81 | 4 | 3634 | 131878 | 2808.75 | 4330 | 115 |
| 1 document, uncached, 1 field | 44 | 38 | 2644 | 70937 | 0.04 | 3730 | 65 |
  

### Analysis

This is using a batch size of one giving a very significant overhead as we need to make each individual write durable
and
have a network round trip. We are seeing only ~5,000 updates/s becauseof the need to use IOPS for flushes – sending
updates
in batches would greatly improve the total throughput.

The most significant factor by far is where the document to be edited is not in cache and many aditional IOPS are used
to
fetch the documents before editing.

### Key Takeaways

* Send updates in batches where possible
* complexity has little impact on speed
* validation has little impact on speed
* using $expr has little imact on speed.

## Comparison of using UpdateOne vs. FindOneAndUpdate and upsert vs. explicit insert

### Description

We add 500,000 1KB documents to a collection. Then we perform either a series of
updates or a mix of updates and inserts where inserts are handled either by
upsert or by a second call to insert when not found for update.

We also try using both UpdateOne() and FindOneAndUpdate() to compare
performance. In both cases 500,000 write ops are performed using 30 threads.

### Performance

### Base Atlas Instance

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M30 | STANDARD | 3000 | 60 |
  


| Percent Inserts | Function | Using Upsert | Time Taken (s) | Update Speed (docs/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| 0 | UpdateOne | false | 167 | 3000 | 7.82 |
| 0 | UpdateOne | true | 178 | 2810 | 8.27 |
| 0 | FindOneAndUpdate | false | 196 | 2550 | 10.04 |
| 0 | FindOneAndUpdate | true | 206 | 2429 | 10.65 |
| 50 | UpdateOne | false | 200 | 2505 | 6.16 |
| 50 | UpdateOne | true | 163 | 3061 | 7.6 |
| 50 | FindOneAndUpdate | false | 214 | 2333 | 6.73 |
| 50 | FindOneAndUpdate | true | 173 | 2883 | 8.33 |
  

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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M40 | STANDARD | 3000 | 200 |
  

### Performance

| Query Type | Time Taken (s) | Speed (Queries/s) |
| --: | --: | --: |
| 1 document, 1 term,  cached, indexed. | 301 | 21884 |
| 1 document, not cached, indexed. | 301 | 3545 |
| 100 documents,1 term, cached, indexed  | 301 | 5372 |
| 100 documents, 1 term, not cached, indexed | 301 | 150 |
| 20 Documents, 1 term, cached | 301 | 11330 |
| 20 Documents, 380 skipped, 1 term, cached | 301 | 6764 |
| 20 documents, 380 range skipped, 1 term, cached | 301 | 10884 |
| 1 Document, 2 terms, partial index, not cached | 302 | 22 |
| 1 Document, 2 terms, compound index,  cached | 301 | 14582 |
| 100 Documents, 100 Terms, indexed, cached | 301 | 3723 |
| 100 Documents, 100 Terms, indexed, not cached | 301 | 35 |
  

### Resource Usage

| Query Type | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | KB/s Network out | O/S IOPS |
| --: | --: | --: | --: | --: | --: |
| 1 document, 1 term,  cached, indexed. | 70 | 0 | 0 | 5237.03 | 10 |
| 1 document, not cached, indexed. | 24 | 64 | 4069 | 908.77 | 3477 |
| 100 documents,1 term, cached, indexed  | 73 | 0 | 2 | 6078.74 | 11 |
| 100 documents, 1 term, not cached, indexed | 25 | 66 | 9204 | 241.9 | 3486 |
| 20 Documents, 1 term, cached | 76 | 0 | 1 | 4581.83 | 12 |
| 20 Documents, 380 skipped, 1 term, cached | 78 | 0 | 1 | 2764.32 | 10 |
| 20 documents, 380 range skipped, 1 term, cached | 77 | 0 | 1 | 4405.03 | 9 |
| 1 Document, 2 terms, partial index, not cached | 10 | 81 | 3960 | 76.63 | 3420 |
| 1 Document, 2 terms, compound index,  cached | 76 | 0 | 1 | 3512.08 | 11 |
| 100 Documents, 100 Terms, indexed, cached | 77 | 0 | 1 | 4234.06 | 13 |
| 100 Documents, 100 Terms, indexed, not cached | 15 | 81 | 3891 | 112.34 | 3474 |
  

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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M40 | STANDARD | 3000 | 200 |
  

### Performance

| Query Type | Time Taken (s) | Speed (Queries/s) |
| --: | --: | --: |
| 1 document, 1 term,  cached, indexed. | 301 | 16750 |
| 10 documents, 1 term,  cached, indexed. | 301 | 14176 |
| 20 documents, 1 term,  cached, indexed. | 301 | 12266 |
| 50 documents, 1 term,  cached, indexed. | 301 | 8469 |
| 100 documents, 1 term,  cached, indexed. | 301 | 5603 |
| 200 documents, 1 term,  cached, indexed. | 301 | 2933 |
| 400 documents, 1 term,  cached, indexed. | 301 | 1649 |
| 1 document, 1 term,  not cached, indexed. | 301 | 16793 |
| 10 documents, 1 term,   not cached,, indexed. | 301 | 13408 |
| 20 documents, 1 term,   not cached,, indexed. | 301 | 10990 |
| 50 documents, 1 term,   not cached,, indexed. | 301 | 3029 |
| 100 documents, 1 term,   not cached,, indexed. | 301 | 154 |
| 200 documents, 1 term,   not cached,, indexed. | 302 | 28 |
| 400 documents, 1 term,   not cached,, indexed. | 303 | 11 |
  

### Resource Usage

| Query Type | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | O/S IOPS |
| --: | --: | --: | --: | --: |
| 1 document, 1 term,  cached, indexed. | 76 | 0 | 1 | 12 |
| 10 documents, 1 term,  cached, indexed. | 76 | 0 | 1 | 12 |
| 20 documents, 1 term,  cached, indexed. | 77 | 0 | 1 | 10 |
| 50 documents, 1 term,  cached, indexed. | 78 | 0 | 1 | 9 |
| 100 documents, 1 term,  cached, indexed. | 73 | 0 | 1 | 11 |
| 200 documents, 1 term,  cached, indexed. | 78 | 0 | 1 | 7 |
| 400 documents, 1 term,  cached, indexed. | 79 | 0 | 1 | 11 |
| 1 document, 1 term,  not cached, indexed. | 71 | 0 | 0 | 8 |
| 10 documents, 1 term,   not cached,, indexed. | 72 | 0 | 1 | 11 |
| 20 documents, 1 term,   not cached,, indexed. | 72 | 0 | 1 | 8 |
| 50 documents, 1 term,   not cached,, indexed. | 74 | 0 | 34598 | 14 |
| 100 documents, 1 term,   not cached,, indexed. | 24 | 66 | 9491 | 3498 |
| 200 documents, 1 term,   not cached,, indexed. | 14 | 78 | 4574 | 3474 |
| 400 documents, 1 term,   not cached,, indexed. | 12 | 81 | 3892 | 3405 |
  

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

| Instance Type | Disk Type | Disk IOPS | Disk Size |
| --: | --: | --: | --: |
| M50 | STANDARD | 3072 | 1024 |
  

### Performance

| Disk Type | Disk  IOPS | Time Taken (s) | Speed (Queries/s) | $ per hour |
| --: | --: | --: | --: | --: |
| PROVISIONED | 1000 | 302 | 18 | 2.33 |
| PROVISIONED | 3000 | 301 | 155 | 3.13 |
| PROVISIONED | 4500 | 301 | 316 | 3.73 |
| PROVISIONED | 6000 | 301 | 570 | 4.33 |
| STANDARD | 3072 | 301 | 180 | 1.77 |
  

### Resource Usage

| Disk Type | Disk IOPS | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | O/S IOPS |
| --: | --: | --: | --: | --: | --: |
| PROVISIONED | 1000 | 4 | 85 | 1267 | 1085 |
| PROVISIONED | 3000 | 11 | 81 | 9558 | 3421 |
| PROVISIONED | 4500 | 19 | 71 | 15953 | 5161 |
| PROVISIONED | 6000 | 31 | 54 | 26306 | 6942 |
| STANDARD | 3072 | 14 | 78 | 10774 | 3543 |
  

### Analysis

Retrieval speed appears to be superlinear with IOPS, a 50% increase in
IOPS results in a doubling of the query speed, a 100% increase in IOPS results
in a quadrupling of the query speed. Conversely, a 66% decrease in IOPS resulted
in an 85% decrease in query speed. This warrants further investigation.

What we do see is reduced IO wait times and increased CPU usage, but it is
unexpected for these to be superlinear.

