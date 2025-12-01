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
| 1 | 411 | 24576 | 61248 | 61.25 |
| 4 | 351 | 24576 | 17929 | 71.72 |
| 32 | 322 | 24576 | 2442 | 78.15 |
| 256 | 333 | 24576 | 295 | 75.52 |
| 2048 | 321 | 24576 | 38 | 78.49 |
  

### Resource Usage

| Document Size (KB) | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 1 | 73 | 0 | 27 | 79199 | 37811 | 508 | 113 | 0 |
| 4 | 70 | 0 | 37 | 90122 | 42479 | 576 | 126 | 0 |
| 32 | 66 | 0 | 36 | 93548 | 44725 | 610 | 133 | 0 |
| 256 | 62 | 11 | 32 | 85306 | 41978 | 556 | 121 | 1 |
| 2048 | 60 | 15 | 10 | 86993 | 42083 | 559 | 119 | 6 |
  

### Analysis

With small document, there are considerably more index entries for the primary
key which we can assume adds some CPU overhead even when they are essentially
sequential. The default volumnes used for these tests are AWS GP3 which have
3,000 IOPS and 125MIB/S write speed. As each inserted document needs to be
inserted in the Oplog, the Write-ahead-log (journal) and the collection we can
see that we are hitting this limit even though we are using only about 18% of
IOPS.

## Impact of client write batch size on write speed

### Description

MongoDB allows you to send multiple write operations to the database in a single
network request. As of MongoDB 8.0 these can even be writes to different
colleciotns.
Conversely, when using simply insertOne(), replaceOne() ( or save() in Spring
Data) then each document is sent individually. This not only incurs a network
round trip per document but also needs each document to independently
wait for durability, requiring a network round trip to a secondary and awaiting
a periodic disk flush on the secondary and primary.

When you send multiple write operations in a single network call, then the cost
of
the durability is shared between all the documents; there can be a
little overhead for a larger batch whilst waiting for the whole batch to be
processed, this still results in much higher throughput, albeit with some
additional latency.

When processing single writes or smaller batches, you can use more threads/async
toincrease concurrency, but it is still far less efficient.

In this test we insert 24GB of data ( 6.2 Million 4 KB documents ) using
differing network write batch sizes to illustrate the impact of not incorrectly
batching writes for ingestion. We use 48 threads loading in parallel.

### Performance

| Write Batch Size | Time Taken (s)  | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| 1 | 1594 | 24576 | 3947 | 15.79 | 9 |
| 10 | 536 | 24576 | 11742 | 46.97 | 34 |
| 100 | 407 | 24576 | 15456 | 61.83 | 269 |
| 1000 | 354 | 24576 | 17759 | 71.04 | 2321 |
| 2000 | 356 | 24576 | 17695 | 70.78 | 5074 |
  

### Resource Usage

| Write batch size | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 1 | 71 | 6 | 52 | 21753 | 11071 | 618 | 32 | 0 |
| 10 | 66 | 8 | 107 | 61113 | 28274 | 548 | 85 | 0 |
| 100 | 67 | 5 | 78 | 78430 | 36658 | 514 | 109 | 1 |
| 1000 | 70 | 7 | 210 | 89599 | 42078 | 571 | 124 | 1 |
| 2000 | 73 | 5 | 278 | 89036 | 41925 | 569 | 124 | 1 |
  

### Analysis

With the 48 threads we have we see that individual writes (batch size 1) it
throttled by network hops and disk flushes to <4000 inserts per second - by
batching we get this up to just under 18,000 inserts/second. We are at this
point hitting the 125MB/s write limit of the AWS GP3 volume.

We can see that the optimal bulk insertion size here is 1,000 documents with a
slight drop in speed at 2,000. Notably the IOPS is a little higher for the
single inserts even though the throughput is lower, this is because there are
extra writes/flushes required to make each record separately durable, there is a
lack of amortization of resources.

## Impact of primary key type on write speed

### Description

All Documents in MongoDB ha a primary key defined as the first field inthe
document, this field is always called `_id`. Where the application does not
supply it then the default value is assigned, a Unique ObjectId() value -
ObjectID is a 12 byte GUID where the firat 4 bytes are the time in seconds since
1970, these are therefore approximately sequential.

The `_id` index is a BTree index, inserting mostly sequential values means older
parts of the index do not need to be acessed for writes and new values are
inserted into a small set of blocks reducing the write I/O

By contrast, if a wholly random value is used for `_id` like a UUID then each
new value may need to read or write any part of the index resulting in far more
dirty blocks to be written to disk and more RAM required to cache it.

In the middle ground an ID May have an inituial portion such as an account ID or
Customer ID followed by a timestamp - in this case there will be one active
block per user.

This test looks at the impact of using an ObjectID vs a random UUID versus a
typical
id constructed from AccountId and Timestamp such as you might use to record a
financial
transaction (BUSINESS_ID). In the Oatter case the id is a string srting with ACC
followed by
5 digits for the account and 8 hex characters for the timestamp.
We insert 24 Million 1KB documents and measure the speed of each variant. The
insert batch size was 1,000.

### Performance

| Primary Key format | Time Taken (s) | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| OBJECT_ID | 485 | 24576 | 51917 | 51.92 | 165 |
| UUID | 1218 | 24576 | 20657 | 20.66 | 515 |
| BUSINESS_ID | 1198 | 24576 | 21003 | 21 | 507 |
  

### Resource Usage

| Primary Key format | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| OBJECT_ID | 69 | 5 | 22 | 66235 | 31590 | 459 | 93 | 1 |
| UUID | 91 | 1 | 8974 | 152368 | 12804 | 971 | 103 | 0 |
| BUSINESS_ID | 92 | 0 | 6010 | 118000 | 12866 | 381 | 58 | 0 |
  

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

| Number of secondary indexes | Time Taken (s) | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| 0 | 203 | 12857 | 16201 | 64.8 | 506 |
| 1 | 283 | 12857 | 11617 | 46.47 | 744 |
| 2 | 386 | 12857 | 8534 | 34.14 | 1076 |
| 3 | 549 | 12857 | 5997 | 23.99 | 1615 |
| 4 | 802 | 12857 | 4105 | 16.42 | 2407 |
| 8 | 2689 | 12857 | 1224 | 4.9 | 7822 |
| 16 | 6908 | 12857 | 476 | 1.91 | 18764 |
  

### Resource Usage

| Number of secondary Indexes | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| 0 | 58 | 7 | 28 | 81579 | 38384 | 516 | 114 | 1 |
| 1 | 72 | 0 | 3015 | 108673 | 27525 | 487 | 93 | 1 |
| 2 | 81 | 0 | 4394 | 103358 | 20220 | 407 | 77 | 0 |
| 3 | 87 | 0 | 5459 | 103360 | 14208 | 344 | 61 | 0 |
| 4 | 91 | 0 | 6054 | 102429 | 9726 | 273 | 49 | 0 |
| 8 | 93 | 0 | 5556 | 88032 | 2900 | 228 | 32 | 0 |
| 16 | 94 | 0 | 5638 | 83196 | 1129 | 819 | 49 | 0 |
  

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

| Disk Specification | Time Taken (s) | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| PROVISIONED (60GB@3000IOPS) | 804 | 12857 | 4096 | 16.38 | 2463 |
| STANDARD (2048GB@6000IOPS) | 810 | 12857 | 4064 | 16.26 | 2466 |
| STANDARD (60GB@3000IOPS) | 833 | 12857 | 3953 | 15.81 | 2515 |
  

### Resource Usage

| Disk Specification | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| PROVISIONED (60GB@3000IOPS) | 91 | 0 | 5960 | 102477 | 9704 | 254 | 47 | 0 |
| STANDARD (2048GB@6000IOPS) | 91 | 0 | 5912 | 100851 | 9629 | 257 | 48 | 0 |
| STANDARD (60GB@3000IOPS) | 92 | 0 | 6181 | 102807 | 9366 | 254 | 47 | 0 |
  

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

| Instance Type | Time Taken (s) | Data Loaded (MB) | Speed (docs/s) | Speed (MB/s) | Average Op Latency (ms) |
| --: | --: | --: | --: | --: | --: |
| M30 | 5088 | 46875 | 2358 | 9.43 | 10806 |
| M40 | 703 | 46875 | 17072 | 68.29 | 1292 |
| M50 | 432 | 46875 | 27772 | 111.09 | 778 |
| M60 | 362 | 46875 | 33182 | 132.73 | 622 |
| M80 | 239 | 46875 | 50275 | 201.1 | 297 |
  

### Resource Usage

| Disk Specification | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| M30 | 92 | 0 | 5133 | 87797 | 5588 | 409 | 47 | 0 |
| M40 | 87 | 0 | 7815 | 193188 | 40449 | 2436 | 197 | 1 |
| M50 | 40 | 17 | 6144 | 218253 | 65804 | 2881 | 249 | 0 |
| M60 | 23 | 11 | 4840 | 288562 | 78621 | 3157 | 284 | 0 |
| M80 | 13 | 7 | 4125 | 349582 | 119119 | 2554 | 350 | 0 |
  

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
  

## Comparison of using UpdateOne vs. FindOneAnd Update and upsert vs. insert

### Description

We add 500,000 1KB documents to a collection. Then we perform either a series of
updates or a mix of updates and
inserts where inserts are handled either by upsert or by a second call to insert
when not found for update.

We also try using both UpdateOne() and FindOneAndUpdate() to compare
performance. In both cases 500,000 write ops are performed using 30 threads.

### Performance

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

| Query Type | Time Taken (s) | Speed (Queries/s) |
| --: | --: | --: |
| 1 document, 1 term,  cached, indexed. | 301 | 11104 |
| 1 document, not cached, indexed. | 301 | 4661 |
| 100 documents,1 term, cached, indexed  | 301 | 1781 |
| 100 documents, 1 term, not cached, indexed | 301 | 342 |
| 20 Documents, 1 term, cached | 301 | 4514 |
| 20 Documents, 380 skipped, 1 term, cached | 301 | 3057 |
| 20 documents, 380 range skipped, 1 term, cached | 301 | 4429 |
| 20 documents, 1 term, not cached | 301 | 4361 |
| 20 documents, 380 skipped, 1 term, not cached | 301 | 2982 |
| 20 documents, 380 range skipped, 1 term, not cached | 301 | 4216 |
| 1 Document, 2 terms, partial index, not cached | 302 | 27 |
| 1 Document, 2 terms, compound index,  cached | 301 | 4725 |
| 100 Documents, 100 Terms, indexed, cached | 301 | 1495 |
  

### Resource Usage

| Query Type | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | O/S IOPS |
| --: | --: | --: | --: | --: |
| 1 document, 1 term,  cached, indexed. | 75 | 0 | 0 | 4 |
| 1 document, not cached, indexed. | 55 | 27 | 4719 | 3516 |
| 100 documents,1 term, cached, indexed  | 79 | 0 | 1 | 5 |
| 100 documents, 1 term, not cached, indexed | 79 | 0 | 23766 | 155 |
| 20 Documents, 1 term, cached | 77 | 0 | 1 | 5 |
| 20 Documents, 380 skipped, 1 term, cached | 78 | 0 | 1 | 5 |
| 20 documents, 380 range skipped, 1 term, cached | 77 | 0 | 1 | 5 |
| 20 documents, 1 term, not cached | 77 | 0 | 4 | 4 |
| 20 documents, 380 skipped, 1 term, not cached | 78 | 0 | 1 | 5 |
| 20 documents, 380 range skipped, 1 term, not cached | 77 | 0 | 1 | 5 |
| 1 Document, 2 terms, partial index, not cached | 25 | 65 | 4954 | 3575 |
| 1 Document, 2 terms, compound index,  cached | 75 | 3 | 4636 | 3569 |
| 100 Documents, 100 Terms, indexed, cached | 78 | 0 | 1 | 5 |
  

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

| Query Type | Time Taken (s) | Speed (Queries/s) |
| --: | --: | --: |
| 1 document, 1 term,  cached, indexed. | 301 | 1883 |
| 10 documents, 1 term,  cached, indexed. | 301 | 1939 |
| 20 documents, 1 term,  cached, indexed. | 301 | 1960 |
| 50 documents, 1 term,  cached, indexed. | 301 | 1971 |
| 100 documents, 1 term,  cached, indexed. | 301 | 1761 |
| 200 documents, 1 term,  cached, indexed. | 301 | 889 |
| 400 documents, 1 term,  cached, indexed. | 301 | 514 |
| 1 document, 1 term,  not cached, indexed. | 301 | 1761 |
| 10 documents, 1 term,   not cached,, indexed. | 301 | 1605 |
| 20 documents, 1 term,   not cached,, indexed. | 301 | 1568 |
| 50 documents, 1 term,   not cached,, indexed. | 301 | 1231 |
| 100 documents, 1 term,   not cached,, indexed. | 301 | 384 |
| 200 documents, 1 term,   not cached,, indexed. | 302 | 44 |
| 400 documents, 1 term,   not cached,, indexed. | 303 | 12 |
  

### Resource Usage

| Query Type | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | O/S IOPS |
| --: | --: | --: | --: | --: |
| 1 document, 1 term,  cached, indexed. | 19 | 0 | 1 | 4 |
| 10 documents, 1 term,  cached, indexed. | 26 | 0 | 1 | 3 |
| 20 documents, 1 term,  cached, indexed. | 36 | 0 | 1 | 3 |
| 50 documents, 1 term,  cached, indexed. | 50 | 0 | 1 | 3 |
| 100 documents, 1 term,  cached, indexed. | 78 | 0 | 1 | 5 |
| 200 documents, 1 term,  cached, indexed. | 76 | 0 | 1 | 4 |
| 400 documents, 1 term,  cached, indexed. | 78 | 0 | 1 | 28 |
| 1 document, 1 term,  not cached, indexed. | 22 | 0 | 1 | 3 |
| 10 documents, 1 term,   not cached,, indexed. | 20 | 0 | 1 | 3 |
| 20 documents, 1 term,   not cached,, indexed. | 24 | 0 | 1 | 4 |
| 50 documents, 1 term,   not cached,, indexed. | 77 | 0 | 23070 | 12 |
| 100 documents, 1 term,   not cached,, indexed. | 80 | 0 | 26563 | 165 |
| 200 documents, 1 term,   not cached,, indexed. | 33 | 54 | 7539 | 3604 |
| 400 documents, 1 term,   not cached,, indexed. | 23 | 68 | 4410 | 3565 |
  

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

| Disk Type | Disk  IOPS | Time Taken (s) | Speed (Queries/s) | $ per hour |
| --: | --: | --: | --: | --: |
| PROVISIONED | 1000 | 301 | 376 | 2.33 |
| PROVISIONED | 3000 | 301 | 1103 | 3.13 |
| PROVISIONED | 4500 | 301 | 1189 | 3.73 |
| PROVISIONED | 6000 | 301 | 1224 | 4.33 |
| STANDARD | 3072 | 301 | 1069 | 1.77 |
  

### Resource Usage

| Disk Type | Disk IOPS | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | O/S IOPS |
| --: | --: | --: | --: | --: | --: |
| PROVISIONED | 1000 | 25 | 65 | 14293 | 1113 |
| PROVISIONED | 3000 | 71 | 4 | 40453 | 925 |
| PROVISIONED | 4500 | 75 | 6 | 43573 | 1039 |
| PROVISIONED | 6000 | 74 | 0 | 45792 | 259 |
| STANDARD | 3072 | 64 | 18 | 39436 | 1513 |
  

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