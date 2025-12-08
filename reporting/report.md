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
| OBJECT_ID | 261 | 24576 | 96333 | 96.33 | 84 |
| UUID | 263 | 24576 | 95678 | 95.68 | 83 |
| BUSINESS_ID | 263 | 24576 | 95704 | 95.7 | 84 |
  

### Resource Usage

| Primary Key format | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| OBJECT_ID | 47 | 7 | 21 | 124236 | 59517 | 877 | 175 | 0 |
| UUID | 47 | 7 | 23 | 124013 | 59112 | 905 | 178 | 0 |
| BUSINESS_ID | 47 | 8 | 22 | 124494 | 59127 | 908 | 177 | 0 |
  

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
| STANDARD (200GB@3000IOPS) | 150 | 12857 | 21939 | 87.76 |  |
| STANDARD (2048GB@6000IOPS) | 810 | 12857 | 4064 | 16.26 | 2466 |
| STANDARD (60GB@3000IOPS) | 833 | 12857 | 3953 | 15.81 | 2515 |
  

### Resource Usage

| Disk Specification | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| PROVISIONED (60GB@3000IOPS) | 91 | 0 | 5960 | 102477 | 9704 | 254 | 47 | 0 |
| STANDARD (200GB@3000IOPS) | 47 | 0 | 5664 | 191388 | 51981 | 2026 | 213 | 0 |
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
| M30 | 4888 | 46875 | 2455 | 9.82 | 10113 |
| M40 | 703 | 46875 | 17072 | 68.29 | 1292 |
| M40 | 698 | 46875 | 17181 | 68.72 | 1325 |
| M50 | 432 | 46875 | 27772 | 111.09 | 778 |
| M50 | 326 | 46875 | 36831 | 147.33 | 469 |
| M60 | 362 | 46875 | 33182 | 132.73 | 622 |
| M60 | 282 | 46875 | 42516 | 170.07 | 348 |
| M80 | 239 | 46875 | 50275 | 201.1 | 297 |
| M80 | 199 | 46875 | 60153 | 240.61 | 218 |
  

### Resource Usage

| Disk Specification | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | O/S IOPS | O/S Write (MB/s) | O/S Read (MB/s) |
| --: | --: | --: | --: | --: | --: | --: | --: | --: |
| M30 | 92 | 0 | 5133 | 87797 | 5588 | 409 | 47 | 0 |
| M30 | 96 | 0 | 5470 | 90152 | 5817 | 453 | 50 | 0 |
| M40 | 87 | 0 | 7815 | 193188 | 40449 | 2436 | 197 | 1 |
| M40 | 88 | 0 | 8388 | 200017 | 40709 | 2674 | 201 | 0 |
| M50 | 40 | 17 | 6144 | 218253 | 65804 | 2881 | 249 | 0 |
| M50 | 52 | 10 | 8012 | 295582 | 87268 | 4582 | 323 | 9 |
| M60 | 23 | 11 | 4840 | 288562 | 78621 | 3157 | 284 | 0 |
| M60 | 29 | 7 | 7568 | 388929 | 100738 | 2794 | 373 | 4 |
| M80 | 13 | 7 | 4125 | 349582 | 119119 | 2554 | 350 | 0 |
| M80 | 7 | 10 | 4526 | 379435 | 142527 | 3736 | 354 | 0 |
  

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