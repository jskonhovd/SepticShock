package edu.gatech.cse8803.features
import java.io.File
import edu.gatech.cse8803.model._
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.linalg._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.util.MLUtils
import org.joda.time._
import org.joda.time.format.DateTimeFormat
import scala.collection.Map
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, FileUtil, Path}
import org.apache.spark.mllib.linalg.{DenseMatrix, Matrices, Vectors, Vector}
import org.apache.spark.mllib.feature.StandardScaler

object Joda {
    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
}
import Joda._


object FeatureConstruction {

  /**
   * ((patient-id, feature-name), feature-value)
   */
  type FeatureTuple = ((Int, Int), Double)

  def merge(srcPath: String, dstPath: String): Unit =  {
    val hadoopConfig = new Configuration()
    val hdfs = FileSystem.get(hadoopConfig)
    FileUtil.copyMerge(hdfs, new Path(srcPath), hdfs, new Path(dstPath), false, hadoopConfig, null)
  }


    /**
   * Aggregate feature tuples from lab result with AVERAGE aggregation, use lab from
   * given set of lab test names only and drop all others.
   * @param labResult RDD of lab result
   * @param candidateLab set of candidate lab test name
   * @return RDD of feature tuples
   */
  def constructAverageChartFeatureTuple(chartEvent: RDD[ChartEvent], window: Map[Int, (DateTime, DateTime)]): RDD[FeatureTuple] = {
    /**
     * TODO implement your own code here and remove existing
     * placeholder code
      */

  val sc = chartEvent.context
  val scDateMap = sc.broadcast(window)
  
  val candidate_MAP = (x:Int, y:DateTime) => (y.isAfter(scDateMap.value(x)._1)) && (y.isBefore(scDateMap.value(x)._2))


  val l = chartEvent.filter(x => candidate_MAP(x.subjectID, x.chartTime))

  l.take(5).foreach(println)
  val l1 = l.map(x => ((x.subjectID, x.itemID), x.numericValue.toDouble))
  val l2 = l.map(x => ((x.subjectID, x.itemID), 1))

  val ll1 = l1.reduceByKey((_+_))
  val ll2 = l2.reduceByKey((_+_))
  val joinL = ll1.join(ll2)
  val l3 = joinL.map{case (k,v) => (k, v._1/v._2)}

  
  l3.take(5).foreach(println)
  l3
}


  /**
   * Given a feature tuples RDD, construct features in vector
   * format for each patient. feature name should be mapped
   * to some index and convert to sparse feature format.
   * @param sc SparkContext to run
   * @param feature RDD of input feature tuples
   * @return
   */
  def construct(sc: SparkContext, feature: RDD[FeatureTuple]): RDD[(Int, Vector)] = {

    /** save for later usage */
    feature.cache()


    val fMap = feature.groupBy(x => x._1._1).map(x => (x._1, (x._2.map(r => (r._1._2, r._2)))))


    //fMap.take(5).foreach(println)

    val featureMap = fMap.flatMap(_._2.map(r => r._1)).distinct.collect.zipWithIndex.toMap

    //featureMap.foreach(println)
    //val fMap = feature.map(x => x._1._2).distinct.collect.zipWithIndex.toMap
    val scFeatureMap = sc.broadcast(featureMap)
    val finalSamples = fMap.map{case
      (target, features) =>
      val numFeatures = scFeatureMap.value.size
      val indexedFeatures = features.
        toList.
        map{case(featureName, featureValue) => (scFeatureMap.value(featureName), featureValue)}

      val featureVector = Vectors.sparse(numFeatures, indexedFeatures)
      val labeledPoint = (target, featureVector)
      labeledPoint

    }


    val result = finalSamples
    //result.take(3).foreach(println)

    result
    /** The feature vectors returned can be sparse or dense. It is advisable to use sparse */

  }

  def write_1(file_name: String, final_sample: RDD[(Int, Int, Vector)]): Unit = {
    val dir = "spark_output/"
    val tmp_file = dir + "tmp_" + file_name
    val file = dir + file_name

    FileUtil.fullyDelete(new File(file))
    FileUtil.fullyDelete(new File(tmp_file))

    val data = final_sample.map(x => LabeledPoint(x._1.toDouble, x._3))

    //val ret = data.map { case LabeledPoint(label, features) =>
    //  val sb = new StringBuilder(label.toString)
    //  features.foreachActive { case (i, v) =>
    //    sb += ' '
    //    sb ++= s"${i + 1}:$v"
    //  }
    //  sb.mkString
    //}
    //ret.saveAsTextFile(tmp_file)
    MLUtils.saveAsLibSVMFile(data, tmp_file)
    merge(tmp_file, file)
  }

  def write_2(file_name: String, final_sample: RDD[(Int, Int, Vector)]): Unit = {
    val dir = "spark_output/"
    val tmp_file = dir + "tmp_" + file_name
    val file = dir + file_name

    FileUtil.fullyDelete(new File(file))
    FileUtil.fullyDelete(new File(tmp_file))
    val data = final_sample.map(x => LabeledPoint(x._2.toDouble, x._3))

    //val ret = data.map { case LabeledPoint(label, features) =>
    //  val sb = new StringBuilder(label.toString)
    //  features.foreachActive { case (i, v) =>
    //    sb += ' '
    //    sb ++= s"${i + 1}:$v"
    //  }
    //  sb.mkString
    //}   
    //ret.saveAsTextFile(tmp_file)

    MLUtils.saveAsLibSVMFile(data, tmp_file)
    merge(tmp_file, file)
  }

  def write_3(file_name: String, final_sample: RDD[(Int, Int, Vector)]): Unit = {
    val dir = "spark_output/"
    val tmp_file = dir + "tmp_" + file_name
    val file = dir + file_name

    FileUtil.fullyDelete(new File(file))
    FileUtil.fullyDelete(new File(tmp_file))
    val data = final_sample.map(x => (x._1, x._2))

    val ret = data.map{ case (pat, target) => pat.toString + "," + target.toString }
    ret.saveAsTextFile(tmp_file)
    merge(tmp_file, file)
  }

  def write_4(file_name: String, final_sample: RDD[(Int, Int, Vector)]): Unit = {
    val dir = "spark_output/"
    val tmp_file = dir + "tmp_" + file_name
    val file = dir + file_name

    FileUtil.fullyDelete(new File(file))
    FileUtil.fullyDelete(new File(tmp_file))

    val rawFeatures = final_sample.map(x => (x._1.toString, x._3))
    

    val scaler = new StandardScaler(withMean = true, withStd = true).fit(rawFeatures.map(_._2))
    val features = rawFeatures.map({ case (patientID, featureVector) => (patientID, scaler.transform(Vectors.dense(featureVector.toArray)))})

    

    val data = features.map(x => LabeledPoint(x._1.toDouble, x._2))
    //val ret = data.map { case LabeledPoint(label, features) =>
    //  val sb = new StringBuilder(label.toString)
    //  features.foreachActive { case (i, v) =>
    //    sb += ' '
    //    sb ++= s"${i + 1}:$v"
    //  }
    //  sb.mkString
    //}   
    //ret.saveAsTextFile(tmp_file)

    MLUtils.saveAsLibSVMFile(data, tmp_file)
    merge(tmp_file, file)
  }

  def write_5(file_name: String, final_sample: RDD[(Int, Int, Vector)]): Unit = {
    val dir = "spark_output/"
    val tmp_file = dir + "tmp_" + file_name
    val file = dir + file_name

    FileUtil.fullyDelete(new File(file))
    FileUtil.fullyDelete(new File(tmp_file))

    val rawFeatures = final_sample.map(x => (x._2.toString, x._3))
    

    val scaler = new StandardScaler(withMean = true, withStd = true).fit(rawFeatures.map(_._2))
    val features = rawFeatures.map({ case (patientID, featureVector) => (patientID, scaler.transform(Vectors.dense(featureVector.toArray)))})

    

    val data = features.map(x => LabeledPoint(x._1.toDouble, x._2))
    //val ret = data.map { case LabeledPoint(label, features) =>
    //  val sb = new StringBuilder(label.toString)
    //  features.foreachActive { case (i, v) =>
    //    sb += ' '
    //    sb ++= s"${i + 1}:$v"
    //  }
    //  sb.mkString
    //}   
    //ret.saveAsTextFile(tmp_file)

    MLUtils.saveAsLibSVMFile(data, tmp_file)
    merge(tmp_file, file)
  }


 def write_features(final_sample: RDD[(Int, Int, Vector)]): Unit = {

   FeatureConstruction.write_1("patient_vector.libsvm", final_sample)
   FeatureConstruction.write_2("target_vector.libsvm", final_sample)
   FeatureConstruction.write_3("patient_target.csv", final_sample)
   FeatureConstruction.write_4("scaled_patient_vector.libsvm", final_sample)
   FeatureConstruction.write_5("scaled_target_vector.libsvm", final_sample)
        /** scale features */
    //val scaler = new StandardScaler(withMean = true, withStd = true).fit(rawFeatures.map(_._2))
    //val features = rawFeatures.map({ case (patientID, featureVector) => (patientID, scaler.transform(Vectors.dense(featureVector.toArray)))})

  }



}
