/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dist_app_environment.wordcount_flink;

import java.time.Duration;
import java.util.StringTokenizer;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.MultipleParameterTool;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.util.Collector;
import org.apache.flink.util.TimeUtils;

/**
 * Implements the "WordCount" program that computes a simple word occurrence histogram over text
 * files. This Job can be executed in both streaming and batch execution modes. Adopted from
 * https://github.com/apache/flink/blob/master/flink-examples/flink-examples-streaming/src/main/java/org/apache/flink/streaming/examples/wordcount/WordCount.java
 *
 * <p>The input is a [list of] plain text file[s] with lines separated by a newline character.
 *
 * <p>Usage:
 *
 * <ul>
 *   <li><code>--input &lt;path&gt;</code>A list of input files and / or directories to read.
 *   <li><code>--discovery-interval &lt;duration&gt;</code>Turns the file reader into a continuous
 *       source that will monitor the provided input directories every interval and read any new
 *       files.
 *   <li><code>--output &lt;path&gt;</code>The output directory where the Job will write the
 *       results.
 * </ul>
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Write a simple Flink DataStream program
 *   <li>Use tuple data types
 *   <li>Write and use a user-defined function
 * </ul>
 */
public class WordCount {

  public static void main(String[] args) throws Exception {
    // Create the execution environment. This is the main entrypoint to building a Flink
    // application.
    final StreamExecutionEnvironment environment =
        StreamExecutionEnvironment.getExecutionEnvironment();
    // By setting the runtime mode to AUTOMATIC, Flink will choose BATCH if all sources are bounded
    // and otherwise STREAMING.
    environment.setRuntimeMode(RuntimeExecutionMode.AUTOMATIC);
    // This optional step makes the input parameters available in the Flink UI.
    // https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/application_parameters/
    final MultipleParameterTool parameter = MultipleParameterTool.fromArgs(args);
    environment.getConfig().setGlobalJobParameters(parameter);

    // Create a new file source that will read files from a given set of directories.
    // Each file will be processed as plain text and split based on newlines.
    final Path[] inputPaths =
        parameter.getMultiParameterRequired("input").stream().map(Path::new).toArray(Path[]::new);
    final FileSource.FileSourceBuilder<String> inputSourceBuilder =
        FileSource.forRecordStreamFormat(new TextLineInputFormat(), inputPaths);
    // If a discovery interval is provided, the source will continuously watch the given directories
    // for new files.
    if (parameter.has("discovery-interval")) {
      inputSourceBuilder.monitorContinuously(
          TimeUtils.parseDuration(parameter.get("discovery-interval")));
    }

    final DataStream<String> inputDS =
        environment.fromSource(
            inputSourceBuilder.build(), WatermarkStrategy.noWatermarks(), "file-input");
    // The text lines read from the source are split into words using a user-defined function.
    // The tokenizer, implemented below, will output each word as a (2-tuple) containing (word, 1)
    final DataStream<Tuple2<String, Integer>> tokensDS =
        inputDS.flatMap(new Tokenizer()).name("tokenizer");
    final DataStream<Tuple2<String, Integer>> firstCharDS =
        tokensDS.map(new FirstCharMapper()).name("first-char-mapper");
    // keyBy groups tuples based on the "0" field, the word.
    // Using a keyBy allows performing aggregations and other stateful transformations over data on
    // a per-key basis.
    // This is similar to a GROUP BY clause in a SQL query.
    final KeyedStream<Tuple2<String, Integer>, String> groupedDS =
        firstCharDS.keyBy(value -> value.f0);
    // For each key, we perform a simple sum of the "1" field, the count.
    // If the input data stream is bounded, sum will output a final count for each word.
    // If it is unbounded, it will continuously output updates each time it sees a new instance of
    // each word in the stream.
    final DataStream<Tuple2<String, Integer>> countedDS = groupedDS.sum(1).name("counter");

    // Given an output directory, Flink will write the results to a file using a simple string
    // encoding.
    // In a production environment, this might be something more structured like CSV, Avro, JSON, or
    // Parquet.
    final Path output = new Path(parameter.getRequired("output"));
    final DefaultRollingPolicy<Tuple2<String, Integer>, String> rollingPolicy =
        DefaultRollingPolicy.builder()
            .withMaxPartSize(MemorySize.ofMebiBytes(1))
            .withRolloverInterval(Duration.ofSeconds(10))
            .build();
    final FileSink<Tuple2<String, Integer>> fileSink =
        FileSink.<Tuple2<String, Integer>>forRowFormat(output, new SimpleStringEncoder<>())
            .withRollingPolicy(rollingPolicy)
            .build();
    countedDS.sinkTo(fileSink).name("file-sink");

    // Apache Flink applications are composed lazily.
    // Calling execute submits the Job and begins processing.
    environment.execute("WordCount");
  }

  /**
   * Implements the string tokenizer that splits sentences into words as a user-defined
   * FlatMapFunction. The function takes a line (String) and splits it into multiple pairs in the
   * form of "(word,1)" ({@code Tuple2<String, Integer>}).
   */
  public static final class Tokenizer implements FlatMapFunction<String, Tuple2<String, Integer>> {
    @Override
    public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
      final StringTokenizer itr = new StringTokenizer(value);
      while (itr.hasMoreTokens()) {
        out.collect(new Tuple2<>(itr.nextToken(), 1));
      }
    }
  }

  public static final class FirstCharMapper implements MapFunction<Tuple2<String, Integer>, Tuple2<String, Integer>> {
    @Override
    public Tuple2<String, Integer> map(Tuple2<String, Integer> value) {
      return new Tuple2<>(String.valueOf(value.f0.charAt(0)), value.f1);
    }
  }
}
