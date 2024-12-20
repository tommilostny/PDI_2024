# Flink WordCount Example

The example runs a [WordCount example](https://github.com/apache/flink/blob/master/flink-examples/flink-examples-streaming/src/main/java/org/apache/flink/streaming/examples/wordcount/WordCount.java) in Java in a Flink cluster.

## Running in Flink local Mode

```shell
./gradlew installDist
./build/install/wordcount-flink/bin/wordcount-flink --input <local_in> [--input <local_in>...] --output <local_out>
```

or

```shell
./gradlew run --args="--input <local_in> [--input <local_in>...] --output <local_out>"
```

## Running in Flink Cluster

The application can be [launched by the `flink` tool](https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/resource-providers/standalone/overview/) in Flink's `bin` directory.

E.g., to run on a Flink standalone cluster, use:

```shell
./gradlew shadowJar
<flink-bin-dir>/start-cluster.sh
<flink-bin-dir>/flink run ./build/libs/wordcount-flink-1.0-SNAPSHOT-all.jar \
  --input <hdfs_in> [--input <hdfs_in>...] --output <hdfs_out>
<flink-bin-dir>/stop-cluster.sh
```
