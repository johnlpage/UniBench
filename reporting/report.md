# MongoDB Universal Benchmark Report

| Author | John Page |
----------
| Date | 2020-09-22 |

## About this document

This document shows the expected performance of MongoDB when performing a given operation. Each table shows the impact
on performance when changing parameters. For example adding an index. This is testing only Database performance, you
shoudl assume that the client is using MongoDB optimally and that there are no network constraints between client and
server.

Unless otherwise specified, this is MongoDB Atlas using an M30 instance with default IOPS on Amazon Web Services.

The intent of this document is to assist in understanding the approximate expected performance of MongoDB either to
verify that your own application running on MongoDB is performing as expected or to help you make decisions about how to
configure your MongoDB instance.

It is clearly not possible to document every possible combination of operations or how a mix of operations will interact
however, this data should allow you to infer that. Notes will be supplied with each test where the results show
something of significance.

## Impact of document size on insert speed

This shows how the document size impacts the insert speed in MB/s


| Kilobytes | MBs | durationMillis | totalKB |
| --- | --- | --- | --- |
  

## Sales Trend

<!-- MONGO_CHART: {  
  "collection": "sales",  
  "pipeline": [  
    {"$group": {"_id": "$month", "value": {"$sum": "$amount"}}},  
    {"$sort": {"_id": 1}},  
    {"$project": {"label": "$_id", "value": 1, "_id": 0}}  
  ],  
  "chartType": "line",  
  "title": "Monthly Sales Trend"  
} -->  
