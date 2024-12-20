package dist_app_environment.peopleinyear_spark

import java.io.File

import org.apache.commons.io.FileUtils
import org.apache.spark.launcher.SparkLauncher
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

object PeopleInYear {
  def main(args: Array[String]) {
    if (args.length < 2) {
      System.err.println(
        "Usage: peopleinyear <hdfs_in> [<hdfs_in>...] <hdfs_out>\n"
          + "hdfs_* args should start with hdfs:// for an HDFS filesystem or file:// for a local filesystem"
      )
      System.exit(2)
    }
    // environment variable of Spark master node defaults to local[*] if the the master URL is absent
    if (System.getProperty(SparkLauncher.SPARK_MASTER) == null) {
      System.setProperty(SparkLauncher.SPARK_MASTER, "local[*]")
    }
    // create a Spark session for the job with respect to the environment variable above
    val spark = SparkSession.builder
      .appName(PeopleInYear.getClass.getSimpleName)
      .getOrCreate()

    /* load and process as DataFrame */
    // read files (based on "fs.defaultFS" for absent schema, "hdfs://..." if set HADOOP_CONF_DIR env-var, or "file://")
    val inputDf = spark.read
      .format("csv")
      .option("header", "true") // uses the first line as names of columns
      .option(
        "inferSchema",
        "true"
      ) // infers the input schema automatically from data (one extra pass over the data)
      .load(args.dropRight(1): _*)
    // get names of columns of individual years, that is all columns except for the first one (the "Name" column)
    val columnNamesForYearsFromDf = inputDf.columns.tail
    // modification: only rows with names starting with "MA"
    val inputDfMod = inputDf.filter(inputDf("JMÉNO").startsWith("MA"))
    // get sum for each year column
    val countsDf = inputDfMod.groupBy().sum(columnNamesForYearsFromDf: _*)
    // modification: only the sum for the year 2016
    val countsDfMod = countsDf.select("sum(2016)")
    // dump results
    val outputPathDf = args.last + "/as-dataframe"
    FileUtils.deleteDirectory(new File(outputPathDf))
    countsDfMod.write.format("csv").option("header", "true").save(outputPathDf)

    /* load as DataFrame and process as RDD (utilize `inputDF` and `columnNamesForYears` defined above) */
    // convert the input data in DataFrame to RDD
    val inputRddFromDf = inputDf.rdd
    val inputRddFromDfMod = inputRddFromDf.filter(row => row.getString(row.fieldIndex("JMÉNO")).startsWith("MA"))
    // get counts for each year and name, that is (year,count) pairs; explicit typing for successful reduce below
    val yearsRddFromDf: RDD[(String, Int)] =
      inputRddFromDfMod.flatMap(row => row.getValuesMap(columnNamesForYearsFromDf))
    val yearsRddFromDfMod = yearsRddFromDf.filter(_._1 == "2016")
    // reduce by key (year)
    val countsRddFromDf = yearsRddFromDfMod.reduceByKey(_ + _)
    // dump results
    val outputPathRddFromDf = args.last + "/as-rdd-from-dataframe"
    FileUtils.deleteDirectory(new File(outputPathRddFromDf))
    countsRddFromDf.saveAsTextFile(outputPathRddFromDf)

    /* load and process as RDD */
    // read files (based on "fs.defaultFS" for absent schema, "hdfs://..." if set HADOOP_CONF_DIR env-var, or "file://")
    val inputRdd = spark.sparkContext.textFile(args.dropRight(1).mkString(","))
    // get names of columns of individual years, that is all coma-separated items except for the first one (i.e., "Name")
    val columnNamesForYearsFromRdd = inputRdd.first().split(',').tail
    // drop the header (the first line)
    val inputWithoutHeadersRdd = inputRdd.mapPartitionsWithIndex((idx, itr) =>
      if (idx == 0) itr.drop(1) else itr
    )
    val inputWithoutHeadersRddMod = inputWithoutHeadersRdd.filter(_.startsWith("MA"))
    // get counts for each year and name, that is (year,count) pairs,
    // by zipping column names and values (all but the first) as integers of each row and flattening the result
    val yearsRdd = inputWithoutHeadersRddMod.flatMap(row =>
      columnNamesForYearsFromRdd.zip(row.split(',').tail.map(_.toInt))
    )
    val yearsRddMod = yearsRdd.filter(_._1 == "2016")
    // reduce by key (year)
    val countsRdd = yearsRddMod.reduceByKey(_ + _)
    // dump results
    val outputPathRdd = args.last + "/as-rdd"
    FileUtils.deleteDirectory(new File(outputPathRdd))
    countsRdd.saveAsTextFile(outputPathRdd)
  }
}
