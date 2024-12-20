package dist_app_environment.peopleinyear_mapreduce;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

public class PeopleInYear {

  /**
   * Create and initialize a new MapReduce job for this class.
   *
   * @param configuration Hadoop configuration
   * @return the job
   * @throws IOException error in getting an instance of the job
   */
  public static Job createJob(Configuration configuration) throws IOException {
    final Job job = Job.getInstance(configuration, PeopleInYear.class.getSimpleName());
    job.setJarByClass(PeopleInYear.class);
    job.setMapperClass(YearCountsMapper.class);
    job.setCombinerClass(LongSumReducer.class);
    job.setReducerClass(LongSumReducer.class);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(LongWritable.class);
    return job;
  }

  /** MapReduce Mapper */
  public static class YearCountsMapper extends Mapper<LongWritable, Text, Text, LongWritable> {

    private static final String CSV_HEADER_PREFIX = "JMÉNO,";
    private static final int FIRST_YEAR = 1900;
    private static final int LAST_YEAR = 2016;
    private final LongWritable longReusable = new LongWritable();
    private final Text textReusable = new Text();

    public void map(LongWritable key, Text value, Context context)
        throws IOException, InterruptedException {
      // convert each line of data to string for processing
      final String line = value.toString();
      // skip the CSV header line
      if ((key.get() == 0) && line.startsWith(CSV_HEADER_PREFIX)) {
        return;
      }
      // split up each element of the line by dashes
      final String[] elements = line.split(",");
      // sum for years in the processed line (we expect to find values/columns for all years)

      for (int year = FIRST_YEAR; year <= LAST_YEAR; year++) {
        final int columnNumber = year - FIRST_YEAR + 1; // skip the first column (name)
        textReusable.set(Integer.toString(year));
        longReusable.set(Integer.parseInt(elements[columnNumber]));
        context.write(textReusable, longReusable);
      }
    }
  }

  /** MapReduce Reducer */
  public static class LongSumReducer extends Reducer<Text, LongWritable, Text, LongWritable> {

    private final LongWritable longReusable = new LongWritable();

    public void reduce(Text key, Iterable<LongWritable> values, Context context)
        throws IOException, InterruptedException {
      long sum = 0;
      for (LongWritable val : values) {
        sum += val.get();
      }
      longReusable.set(sum);
      context.write(key, longReusable);
    }
  }
}
