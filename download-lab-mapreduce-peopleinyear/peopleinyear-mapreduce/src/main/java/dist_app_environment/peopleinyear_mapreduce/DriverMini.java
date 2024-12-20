package dist_app_environment.peopleinyear_mapreduce;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.MRConfig;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.server.jobtracker.JTConfig;
import org.apache.hadoop.util.GenericOptionsParser;

public class DriverMini {

  private static final File APP_TMP_DIR = Files.createTempDir();
  private static final Path DFS_INPUT_DIR = new Path("peopleinyear/input");
  private static final Path DFS_OUTPUT_DIR = new Path("peopleinyear/output");

  public static void main(String[] args) throws Exception {
    // disable IPv6: not supported by Hadoop and results into error "Relative path in absolute URI"
    // for Block Pool
    // `hostname` must have an IPv4 address, check it by: host -vt A `hostname`
    System.setProperty("java.net.preferIPv4Stack", "true");
    // conf
    final Configuration conf =
        new HdfsConfiguration(
            false); // without defaults to isolate from existing Hadoop configuration
    final String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
    if (otherArgs.length < 2) {
      System.err.println("Usage: peopleinyear <local_in> [<local_in>...] <local_out>");
      System.exit(2);
    }
    // cluster start
    conf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, normalizeHadoopPath(APP_TMP_DIR));
    conf.set(
        DFSConfigKeys.DFS_NAMENODE_EDITS_DIR_KEY,
        "file://" + normalizeHadoopPath(createSubDir(APP_TMP_DIR, "name")));
    conf.set(
        JobConf.MAPRED_LOCAL_DIR_PROPERTY,
        normalizeHadoopPath(createSubDir(APP_TMP_DIR, "mr-local")));
    conf.set(JTConfig.JT_SYSTEM_DIR, normalizeHadoopPath(createSubDir(APP_TMP_DIR, "mr-system")));
    conf.set(
        JTConfig.JT_STAGING_AREA_ROOT,
        normalizeHadoopPath(createSubDir(APP_TMP_DIR, "mr-staging")));
    conf.set(MRConfig.TEMP_DIR, normalizeHadoopPath(createSubDir(APP_TMP_DIR, "mr-temp")));
    try (MiniDFSCluster miniDFSCluster = new MiniDFSCluster.Builder(conf).build()) {
      final FileSystem distributedFileSystem = miniDFSCluster.getFileSystem();
      final FileSystem localFileSystem = FileSystem.getLocal(conf);
      // HDFS init directories
      distributedFileSystem.delete(DFS_INPUT_DIR, true);
      distributedFileSystem.delete(DFS_OUTPUT_DIR, true);
      distributedFileSystem.mkdirs(DFS_INPUT_DIR);
      // HDFS copy input files from the local filesystem
      final Path[] srcPaths;
      // in Java 8:
      // srcPaths = IntStream.range(0, otherArgs.length - 1).mapToObj(i -> new
      // Path(otherArgs[i])).toArray(Path[]::new);
      // in Java 7:
      final List<Path> srcPathsList = new LinkedList<>();
      for (int i = 0; i < otherArgs.length - 1; i++) {
        srcPathsList.add(new Path(otherArgs[i]));
      }
      srcPaths = new Path[srcPathsList.size()];
      srcPathsList.toArray(srcPaths);
      //
      distributedFileSystem.copyFromLocalFile(false, true, srcPaths, DFS_INPUT_DIR);
      // job
      final Job job = PeopleInYear.createJob(conf);
      FileInputFormat.addInputPath(job, DFS_INPUT_DIR);
      FileOutputFormat.setOutputPath(job, DFS_OUTPUT_DIR);
      job.waitForCompletion(true);
      // HDFS dump resulting files to the local filesystem
      if (job.isSuccessful()) {
        final Path outputPath = new Path(otherArgs[otherArgs.length - 1]);
        localFileSystem.delete(outputPath, true);
        localFileSystem.mkdirs(outputPath);
        for (FileStatus file : distributedFileSystem.listStatus(DFS_OUTPUT_DIR)) {
          final Path filePath = file.getPath();
          distributedFileSystem.copyToLocalFile(
              true, filePath, new Path(outputPath, filePath.getName()));
        }
      }
      // cluster stop
      miniDFSCluster.shutdown(true);
    }
  }

  private static String normalizeHadoopPath(File path) throws IOException {
    // Windows path is including a drive letter and with backslashes; convert to a posix path
    // Unix path is OK
    final String absolutePath = path.getCanonicalPath(); // canonical is both absolute and unique
    return SystemUtils.IS_OS_WINDOWS ? ("/" + absolutePath.replace('\\', '/')) : absolutePath;
  }

  private static File createSubDir(File parent, String child) throws IOException {
    final File dir = new File(parent, child);
    FileUtils.forceMkdir(dir);
    return dir;
  }
}
