// adopted from
// https://github.com/qihongchen/flink-java-example-app-gradle/blob/main/src/test/java/flink/example/app/wordcount/WordCountTest.java

package dist_app_environment.wordcount_flink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.Test;

public class WordCountTest {

  @Test
  public void testWordCountTokenizer() {

    final SimpleCollector<Tuple2<String, Integer>> collector = new SimpleCollector<>();
    final WordCount.Tokenizer tokenizer = new WordCount.Tokenizer();

    tokenizer.flatMap("hello world hello you", collector);

    @SuppressWarnings("rawtypes")
    final List expectedResult =
        Arrays.asList(
            Tuple2.of("hello", 1),
            Tuple2.of("world", 1),
            Tuple2.of("hello", 1),
            Tuple2.of("you", 1));

    assertEquals(expectedResult, collector.getRecords());
  }

  private static class SimpleCollector<T> implements Collector<T> {
    private final List<T> records = new ArrayList<>();

    @Override
    public void collect(T record) {
      records.add(record);
    }

    @Override
    public void close() {}

    private List<T> getRecords() {
      return records;
    }

    private void reset() {
      records.clear();
    }
  }
}
