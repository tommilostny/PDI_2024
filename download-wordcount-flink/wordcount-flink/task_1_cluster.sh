#!/bin/sh

./build.sh

# input and output directories are in HDFS as set by fs.default-scheme config option
exec flink run build/libs/*-SNAPSHOT.jar --input "/user/${USER}/cetnost-jmena-dnar-2016/jmena.txt" --output "/user/${USER}/jmena-wordcount.flink_cluster_output"
