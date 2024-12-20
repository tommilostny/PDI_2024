#!/bin/sh

./build.sh

# input and output directories are in HDFS, not in a local filesystem (use hdfs dfs -put/-get to upload and download them)
# DataStreamer exception java.lang.InterruptedException is not an error
# for the output, see: jmena-wordcount.spark_output/as-dataframe
# Scala version in the application build and in the Spark must be identical, otherwise, there will be "main" java.lang.NoSuchMethodError: scala.Predef$.refArrayOps([Ljava/lang/Object;)[Ljava/lang/Object;
exec spark-submit build/libs/*-SNAPSHOT.jar cetnost-jmena-dnar-2016/cetnost-jmena-dnar.csv peopleinyear.spark_output
