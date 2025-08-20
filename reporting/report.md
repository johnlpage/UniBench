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

```
[
  {
    "docSizeKB": 1,
    "totalMB": 24576,
    "durationS": 448,
    "MBperSecond": 56.12,
    "DocsPerSecond": 56115
  },
  {
    "docSizeKB": 4,
    "totalMB": 24576,
    "durationS": 374,
    "MBperSecond": 67.23,
    "DocsPerSecond": 16808
  },
  {
    "docSizeKB": 32,
    "totalMB": 24576,
    "durationS": 363,
    "MBperSecond": 69.4,
    "DocsPerSecond": 2169
  },
  {
    "docSizeKB": 256,
    "totalMB": 24576,
    "durationS": 333,
    "MBperSecond": 75.52,
    "DocsPerSecond": 295
  },
  {
    "docSizeKB": 2048,
    "totalMB": 24576,
    "durationS": 321,
    "MBperSecond": 78.49,
    "DocsPerSecond": 38
  }
]
```
  

### Resource Usage

| Document Size (KB) | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | Predicted IOPS | Actual mean IOPS |
| --: | --: | --: | --: | --: | --: | --: | --: |
| 1 | 72 | 4 | 39 | 72761 | 34643 | 459 | 466 |
| 4 | 64 | 8 | 37 | 84520 | 39824 | 523 | 536 |
| 32 | 64 | 9 | 42 | 83136 | 39721 | 522 | 535 |
| 256 | 62 | 11 | 32 | 85306 | 41978 | 529 | 556 |
| 2048 | 60 | 15 | 10 | 86993 | 42083 | 514 | 559 |

```
[
  {
    "_id": {
      "docSizeKB": 1,
      "testname": "insert_docsize",
      "totalDocsToInsert": 25165824,
      "writeBatchSize": 4000
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T12:12:07Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T12:13:07Z",
          "value": 69.08897140858907
        },
        {
          "timestamp": "2025-08-19T12:14:07Z",
          "value": 72.89914216706921
        },
        {
          "timestamp": "2025-08-19T12:15:07Z",
          "value": 76.13439498933903
        },
        {
          "timestamp": "2025-08-19T12:16:07Z",
          "value": 75.00416181122024
        },
        {
          "timestamp": "2025-08-19T12:17:06Z",
          "value": 71.83753651902347
        },
        {
          "timestamp": "2025-08-19T12:18:06Z",
          "value": 72.46907619824529
        },
        {
          "timestamp": "2025-08-19T12:19:06Z",
          "value": 70.61841075265237
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 32630968320,
    "journalWrite": 15535991094,
    "meanIops": 466,
    "cachePageReadPerSecondKB": 39,
    "compressedDataPerSecondKB": 72761,
    "journalPerSecondKB": 34643,
    "docSizeKB": 1,
    "cacheReadInMB": 0,
    "meancpu": 72,
    "iowait": 4,
    "estimatedIOPS": 459
  },
  {
    "_id": {
      "docSizeKB": 4,
      "testname": "insert_docsize",
      "totalDocsToInsert": 6291456,
      "writeBatchSize": 1000
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T12:20:07Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T12:21:07Z",
          "value": 67.29616306954436
        },
        {
          "timestamp": "2025-08-19T12:22:07Z",
          "value": 67.33796411973415
        },
        {
          "timestamp": "2025-08-19T12:23:07Z",
          "value": 65.67927135887574
        },
        {
          "timestamp": "2025-08-19T12:24:07Z",
          "value": 67.30577025586355
        },
        {
          "timestamp": "2025-08-19T12:25:07Z",
          "value": 66.40852693812974
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 31636762624,
    "journalWrite": 14906649531,
    "meanIops": 536,
    "cachePageReadPerSecondKB": 37,
    "compressedDataPerSecondKB": 84520,
    "journalPerSecondKB": 39824,
    "docSizeKB": 4,
    "cacheReadInMB": 0,
    "meancpu": 64,
    "iowait": 8,
    "estimatedIOPS": 523
  },
  {
    "_id": {
      "docSizeKB": 32,
      "testname": "insert_docsize",
      "totalDocsToInsert": 786432,
      "writeBatchSize": 125
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T12:26:07Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T12:27:07Z",
          "value": 63.07536082302608
        },
        {
          "timestamp": "2025-08-19T12:28:07Z",
          "value": 63.114235266682215
        },
        {
          "timestamp": "2025-08-19T12:29:07Z",
          "value": 64.45143465814527
        },
        {
          "timestamp": "2025-08-19T12:30:07Z",
          "value": 68.28703703703704
        },
        {
          "timestamp": "2025-08-19T12:31:07Z",
          "value": 69.27023470424601
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 30145327104,
    "journalWrite": 14403032163,
    "meanIops": 535,
    "cachePageReadPerSecondKB": 42,
    "compressedDataPerSecondKB": 83136,
    "journalPerSecondKB": 39721,
    "docSizeKB": 32,
    "cacheReadInMB": 0,
    "meancpu": 64,
    "iowait": 9,
    "estimatedIOPS": 522
  },
  {
    "_id": {
      "docSizeKB": 256,
      "testname": "insert_docsize",
      "totalDocsToInsert": 98304,
      "writeBatchSize": 16
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T12:32:07Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T12:33:06Z",
          "value": 64.01375292164899
        },
        {
          "timestamp": "2025-08-19T12:34:06Z",
          "value": 63.93306136041962
        },
        {
          "timestamp": "2025-08-19T12:35:06Z",
          "value": 65.24708116120652
        },
        {
          "timestamp": "2025-08-19T12:36:06Z",
          "value": 64.10106932276226
        },
        {
          "timestamp": "2025-08-19T12:37:06Z",
          "value": 63.653474581917514
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 28425539584,
    "journalWrite": 13987747019,
    "meanIops": 556,
    "cachePageReadPerSecondKB": 32,
    "compressedDataPerSecondKB": 85306,
    "journalPerSecondKB": 41978,
    "docSizeKB": 256,
    "cacheReadInMB": 0,
    "meancpu": 62,
    "iowait": 11,
    "estimatedIOPS": 529
  },
  {
    "_id": {
      "docSizeKB": 2048,
      "testname": "insert_docsize",
      "totalDocsToInsert": 12288,
      "writeBatchSize": 2
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T12:38:06Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T12:39:06Z",
          "value": 62.30638553012891
        },
        {
          "timestamp": "2025-08-19T12:40:06Z",
          "value": 66.4934805415397
        },
        {
          "timestamp": "2025-08-19T12:41:06Z",
          "value": 62.29287058470865
        },
        {
          "timestamp": "2025-08-19T12:42:07Z",
          "value": 62.34696427084201
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 27890511872,
    "journalWrite": 13492122719,
    "meanIops": 559,
    "cachePageReadPerSecondKB": 10,
    "compressedDataPerSecondKB": 86993,
    "journalPerSecondKB": 42083,
    "docSizeKB": 2048,
    "cacheReadInMB": 0,
    "meancpu": 60,
    "iowait": 15,
    "estimatedIOPS": 514
  }
]
```
  

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

```
[
  {
    "totalKB": 25165824,
    "opTimeWrites": [
      {
        "timestamp": "2025-08-19T13:43:22Z",
        "value": 8.93044453793297
      },
      {
        "timestamp": "2025-08-19T13:44:22Z",
        "value": 8.695699360100848
      },
      {
        "timestamp": "2025-08-19T13:45:22Z",
        "value": 8.96407994817697
      },
      {
        "timestamp": "2025-08-19T13:46:22Z",
        "value": 8.681995877461084
      },
      {
        "timestamp": "2025-08-19T13:47:22Z",
        "value": 8.759424638413995
      },
      {
        "timestamp": "2025-08-19T13:48:22Z",
        "value": 8.736561086349468
      },
      {
        "timestamp": "2025-08-19T13:49:22Z",
        "value": 8.7707226287822
      },
      {
        "timestamp": "2025-08-19T13:50:22Z",
        "value": 9.131249404199636
      },
      {
        "timestamp": "2025-08-19T13:51:22Z",
        "value": 8.895238216744705
      },
      {
        "timestamp": "2025-08-19T13:52:22Z",
        "value": 8.647975467801489
      },
      {
        "timestamp": "2025-08-19T13:53:22Z",
        "value": 9.049375816768691
      },
      {
        "timestamp": "2025-08-19T13:54:22Z",
        "value": 8.718709247338708
      },
      {
        "timestamp": "2025-08-19T13:55:22Z",
        "value": 8.882455613924613
      },
      {
        "timestamp": "2025-08-19T13:56:22Z",
        "value": 8.838783853913954
      },
      {
        "timestamp": "2025-08-19T13:57:22Z",
        "value": 8.833417933927635
      },
      {
        "timestamp": "2025-08-19T13:58:22Z",
        "value": 8.657889928828281
      },
      {
        "timestamp": "2025-08-19T13:59:22Z",
        "value": 9.058995051585292
      },
      {
        "timestamp": "2025-08-19T14:00:22Z",
        "value": 9.174081867285626
      },
      {
        "timestamp": "2025-08-19T14:01:22Z",
        "value": 8.98728585924909
      },
      {
        "timestamp": "2025-08-19T14:02:22Z",
        "value": 8.97630415030943
      },
      {
        "timestamp": "2025-08-19T14:03:22Z",
        "value": 9.143775961409622
      },
      {
        "timestamp": "2025-08-19T14:04:22Z",
        "value": 8.845013784821475
      },
      {
        "timestamp": "2025-08-19T14:05:22Z",
        "value": 8.817285672867234
      },
      {
        "timestamp": "2025-08-19T14:06:22Z",
        "value": 8.54910284967492
      },
      {
        "timestamp": "2025-08-19T14:07:22Z",
        "value": 8.508951730504506
      }
    ],
    "opLatency": 9,
    "batchsize": 1,
    "totalMB": 24576,
    "durationS": 1594,
    "MBperSecond": 15.79,
    "DocsPerSecond": 3947
  },
  {
    "totalKB": 25165824,
    "opTimeWrites": [
      {
        "timestamp": "2025-08-19T14:09:22Z",
        "value": 32.71629186170878
      },
      {
        "timestamp": "2025-08-19T14:10:22Z",
        "value": 33.381011637937135
      },
      {
        "timestamp": "2025-08-19T14:11:22Z",
        "value": 33.57712428549028
      },
      {
        "timestamp": "2025-08-19T14:12:22Z",
        "value": 32.9389669386725
      },
      {
        "timestamp": "2025-08-19T14:13:22Z",
        "value": 32.78347100507359
      },
      {
        "timestamp": "2025-08-19T14:14:22Z",
        "value": 33.433625941789295
      },
      {
        "timestamp": "2025-08-19T14:15:22Z",
        "value": 35.25844918593413
      },
      {
        "timestamp": "2025-08-19T14:16:22Z",
        "value": 33.91270773638969
      }
    ],
    "opLatency": 34,
    "batchsize": 10,
    "totalMB": 24576,
    "durationS": 536,
    "MBperSecond": 46.97,
    "DocsPerSecond": 11742
  },
  {
    "totalKB": 25165824,
    "opTimeWrites": [
      {
        "timestamp": "2025-08-19T14:18:22Z",
        "value": 259.2243758558938
      },
      {
        "timestamp": "2025-08-19T14:19:22Z",
        "value": 271.6757961091186
      },
      {
        "timestamp": "2025-08-19T14:20:22Z",
        "value": 266.864704006802
      },
      {
        "timestamp": "2025-08-19T14:21:22Z",
        "value": 270.49799414507214
      },
      {
        "timestamp": "2025-08-19T14:22:22Z",
        "value": 275.58466944871515
      },
      {
        "timestamp": "2025-08-19T14:23:22Z",
        "value": 270.6859788929571
      }
    ],
    "opLatency": 269,
    "batchsize": 100,
    "totalMB": 24576,
    "durationS": 407,
    "MBperSecond": 61.83,
    "DocsPerSecond": 15456
  },
  {
    "totalKB": 25165824,
    "opTimeWrites": [
      {
        "timestamp": "2025-08-19T14:25:22Z",
        "value": 2317.945471349353
      },
      {
        "timestamp": "2025-08-19T14:26:22Z",
        "value": 2304.390289449113
      },
      {
        "timestamp": "2025-08-19T14:27:22Z",
        "value": 2259.59095193214
      },
      {
        "timestamp": "2025-08-19T14:28:22Z",
        "value": 2324.5230627306273
      },
      {
        "timestamp": "2025-08-19T14:29:22Z",
        "value": 2396.085959885387
      }
    ],
    "opLatency": 2321,
    "batchsize": 1000,
    "totalMB": 24576,
    "durationS": 354,
    "MBperSecond": 71.04,
    "DocsPerSecond": 17759
  },
  {
    "totalKB": 25165824,
    "opTimeWrites": [
      {
        "timestamp": "2025-08-19T14:31:22Z",
        "value": 5075.5294117647045
      },
      {
        "timestamp": "2025-08-19T14:32:22Z",
        "value": 5030.193370165745
      },
      {
        "timestamp": "2025-08-19T14:33:22Z",
        "value": 5078.420370370371
      },
      {
        "timestamp": "2025-08-19T14:34:22Z",
        "value": 5225.641878669276
      },
      {
        "timestamp": "2025-08-19T14:35:22Z",
        "value": 4961.025735294118
      }
    ],
    "opLatency": 5074,
    "batchsize": 2000,
    "totalMB": 24576,
    "durationS": 356,
    "MBperSecond": 70.78,
    "DocsPerSecond": 17695
  }
]
```
  

### Resource Usage

| Write Batch Size | CPU Usage (%) | Time waiting for I/O (%) | Read into Cache (Pages/s) | Write from Cache (KB/s) | Write to WAL (KB/s) | Predicted IOPS | Actual mean IOPS |
| --: | --: | --: | --: | --: | --: | --: | --: |
| 1 | 71 | 6 | 52 | 21753 | 11071 | 180 | 618 |
| 10 | 66 | 8 | 107 | 61113 | 28274 | 456 | 548 |
| 100 | 67 | 5 | 78 | 78430 | 36658 | 528 | 514 |
| 1000 | 70 | 7 | 210 | 89599 | 42078 | 724 | 571 |
| 2000 | 73 | 5 | 278 | 89036 | 41925 | 790 | 569 |

```
[
  {
    "_id": {
      "testname": "insert_batchsize",
      "writeBatchSize": 1
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T13:42:07Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T13:43:07Z",
          "value": 56.890865480711575
        },
        {
          "timestamp": "2025-08-19T13:44:07Z",
          "value": 59.24778466253581
        },
        {
          "timestamp": "2025-08-19T13:45:07Z",
          "value": 63.761742954227465
        },
        {
          "timestamp": "2025-08-19T13:46:07Z",
          "value": 69.62953091684435
        },
        {
          "timestamp": "2025-08-19T13:47:07Z",
          "value": 59.85510866849862
        },
        {
          "timestamp": "2025-08-19T13:48:07Z",
          "value": 59.431653598361876
        },
        {
          "timestamp": "2025-08-19T13:49:07Z",
          "value": 59.834113355873484
        },
        {
          "timestamp": "2025-08-19T13:50:07Z",
          "value": 57.61255184130315
        },
        {
          "timestamp": "2025-08-19T13:51:07Z",
          "value": 57.8902658404957
        },
        {
          "timestamp": "2025-08-19T13:52:07Z",
          "value": 60.04663169289699
        },
        {
          "timestamp": "2025-08-19T13:53:07Z",
          "value": 58.043076308030585
        },
        {
          "timestamp": "2025-08-19T13:54:07Z",
          "value": 60.08394263919655
        },
        {
          "timestamp": "2025-08-19T13:55:07Z",
          "value": 59.68123376190794
        },
        {
          "timestamp": "2025-08-19T13:56:07Z",
          "value": 61.524624839692876
        },
        {
          "timestamp": "2025-08-19T13:57:06Z",
          "value": 60.30729616642103
        },
        {
          "timestamp": "2025-08-19T13:58:06Z",
          "value": 59.2271818787475
        },
        {
          "timestamp": "2025-08-19T13:59:06Z",
          "value": 59.644944793245294
        },
        {
          "timestamp": "2025-08-19T14:00:06Z",
          "value": 65.34086177318076
        },
        {
          "timestamp": "2025-08-19T14:01:06Z",
          "value": 68.06749283763075
        },
        {
          "timestamp": "2025-08-19T14:02:06Z",
          "value": 59.39274828866942
        },
        {
          "timestamp": "2025-08-19T14:03:06Z",
          "value": 57.739394040341786
        },
        {
          "timestamp": "2025-08-19T14:04:06Z",
          "value": 58.28282155465264
        },
        {
          "timestamp": "2025-08-19T14:05:07Z",
          "value": 58.152508784492674
        },
        {
          "timestamp": "2025-08-19T14:06:07Z",
          "value": 61.38290606832909
        },
        {
          "timestamp": "2025-08-19T14:07:07Z",
          "value": 60.61767057569296
        },
        {
          "timestamp": "2025-08-19T14:08:07Z",
          "value": 60.9821101375887
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 34673102848,
    "journalWrite": 17646090750,
    "meanIops": 618,
    "cachePageReadPerSecondKB": 52,
    "compressedDataPerSecondKB": 21753,
    "journalPerSecondKB": 11071,
    "batchsize": 1,
    "cacheReadInMB": 0,
    "meancpu": 71,
    "iowait": 6,
    "estimatedIOPS": 180
  },
  {
    "_id": {
      "testname": "insert_batchsize",
      "writeBatchSize": 10
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T14:09:07Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T14:10:07Z",
          "value": 62.92361238322037
        },
        {
          "timestamp": "2025-08-19T14:11:07Z",
          "value": 62.90276922052187
        },
        {
          "timestamp": "2025-08-19T14:12:07Z",
          "value": 62.47189514181504
        },
        {
          "timestamp": "2025-08-19T14:13:07Z",
          "value": 62.78206131659145
        },
        {
          "timestamp": "2025-08-19T14:14:07Z",
          "value": 62.56137956289428
        },
        {
          "timestamp": "2025-08-19T14:15:07Z",
          "value": 67.15051436561575
        },
        {
          "timestamp": "2025-08-19T14:16:07Z",
          "value": 68.21018991660979
        },
        {
          "timestamp": "2025-08-19T14:17:07Z",
          "value": 62.35325462508118
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 32746065920,
    "journalWrite": 15149901425,
    "meanIops": 548,
    "cachePageReadPerSecondKB": 107,
    "compressedDataPerSecondKB": 61113,
    "journalPerSecondKB": 28274,
    "batchsize": 10,
    "cacheReadInMB": 0,
    "meancpu": 66,
    "iowait": 8,
    "estimatedIOPS": 456
  },
  {
    "_id": {
      "testname": "insert_batchsize",
      "writeBatchSize": 100
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T14:18:06Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T14:19:07Z",
          "value": 70.8953362706792
        },
        {
          "timestamp": "2025-08-19T14:20:07Z",
          "value": 70.79204628582829
        },
        {
          "timestamp": "2025-08-19T14:21:07Z",
          "value": 71.65731921813294
        },
        {
          "timestamp": "2025-08-19T14:22:07Z",
          "value": 71.54244719011558
        },
        {
          "timestamp": "2025-08-19T14:23:06Z",
          "value": 70.15530047265361
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 31924961280,
    "journalWrite": 14921718960,
    "meanIops": 514,
    "cachePageReadPerSecondKB": 78,
    "compressedDataPerSecondKB": 78430,
    "journalPerSecondKB": 36658,
    "batchsize": 100,
    "cacheReadInMB": 0,
    "meancpu": 67,
    "iowait": 5,
    "estimatedIOPS": 528
  },
  {
    "_id": {
      "testname": "insert_batchsize",
      "writeBatchSize": 1000
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T14:24:06Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T14:25:07Z",
          "value": 78.44018374276013
        },
        {
          "timestamp": "2025-08-19T14:26:07Z",
          "value": 71.01492686336178
        },
        {
          "timestamp": "2025-08-19T14:27:07Z",
          "value": 72.57233122713667
        },
        {
          "timestamp": "2025-08-19T14:28:07Z",
          "value": 71.17416992593826
        },
        {
          "timestamp": "2025-08-19T14:29:07Z",
          "value": 72.13106569926113
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 31741636608,
    "journalWrite": 14906815482,
    "meanIops": 571,
    "cachePageReadPerSecondKB": 210,
    "compressedDataPerSecondKB": 89599,
    "journalPerSecondKB": 42078,
    "batchsize": 1000,
    "cacheReadInMB": 0,
    "meancpu": 70,
    "iowait": 7,
    "estimatedIOPS": 724
  },
  {
    "_id": {
      "testname": "insert_batchsize",
      "writeBatchSize": 2000
    },
    "userCPU": {
      "dataPoints": [
        {
          "timestamp": "2025-08-19T14:30:07Z",
          "value": null
        },
        {
          "timestamp": "2025-08-19T14:31:07Z",
          "value": 82.01049387857083
        },
        {
          "timestamp": "2025-08-19T14:32:07Z",
          "value": 73.69480584301068
        },
        {
          "timestamp": "2025-08-19T14:33:06Z",
          "value": 69.27323218639935
        },
        {
          "timestamp": "2025-08-19T14:34:06Z",
          "value": 67.57341679429979
        },
        {
          "timestamp": "2025-08-19T14:35:06Z",
          "value": 71.1337117366047
        }
      ],
      "name": "SYSTEM_NORMALIZED_CPU_USER",
      "units": "PERCENT"
    },
    "cacheWriteOut": 31656722432,
    "journalWrite": 14906353556,
    "meanIops": 569,
    "cachePageReadPerSecondKB": 278,
    "compressedDataPerSecondKB": 89036,
    "journalPerSecondKB": 41925,
    "batchsize": 2000,
    "cacheReadInMB": 0,
    "meancpu": 73,
    "iowait": 5,
    "estimatedIOPS": 790
  }
]
```
  

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

## To Add

* Ingesting data
    * ~~Document size~~
    * ~~Batching vis Single Insert~~
    * ObjectId vs BusinessID vs UUID
    * number of indexes and cache
    * Index type
    * Iops (Provisioned vi Standard)
    * Instance sizes
* Replacing Data
    * Replace
    * Replace and cache
    * Updates
    * Replace
    * Update
    * Impacted indexes
* Reading Data
    * Retrieval single
    * Retrieval set
    * Retrieval page N
    * Retrieval next page
    * Retrieval part index
    * Retrieval $in
    * Retrieval out of cache
* Aggregation
    * Aggregation group
    * Aggregation ¢lookup
* Deletion