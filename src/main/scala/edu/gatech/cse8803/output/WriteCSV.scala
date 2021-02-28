package edu.gatech.cse8803.output

import java.io.File
import org.apache.spark.mllib.util
import org.apache.spark.sql.SchemaRDD
import org.apache.spark.sql.SQLContext
import com.databricks.spark.csv.CsvContext
import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, FileUtil, Path}
import org.apache.spark.rdd.RDD
import edu.gatech.cse8803.model._
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time._
import org.joda.time.format.DateTimeFormat

object WriteCSV {


  def merge(srcPath: String, dstPath: String): Unit =  {
    val hadoopConfig = new Configuration()
    val hdfs = FileSystem.get(hadoopConfig)
    FileUtil.copyMerge(hdfs, new Path(srcPath), hdfs, new Path(dstPath), false, hadoopConfig, null)
  }


  def writePatient(patient : RDD[Patient]): Unit = {
    val tmp_file = "data_filter/TMP_PATIENTS.csv"
    val file = "data_filter/PATIENTS.csv"
    FileUtil.fullyDelete(new File(file))
    FileUtil.fullyDelete(new File(tmp_file))
    val sc = patient.context
    val header = getPatientHeader(sc)

    val csvPatient = patient.map(x => x.subjectID.toString + ",\"" + x.gender.toString + "\"," +  DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(x.dob))
    val ret = header.union(csvPatient)
   
    ret.saveAsTextFile(tmp_file)
    merge(tmp_file, file)
  }

  def writeICUStay(icuStay : RDD[ICUStay]): Unit = {

    val tmp_file = "data_filter/TMP_ICUSTAYS.csv"
    val file = "data_filter/ICUSTAYS.csv"
    FileUtil.fullyDelete(new File(file))
    FileUtil.fullyDelete(new File(tmp_file))
    val sc = icuStay.context
    val header = getICUHeader(sc)

    val csvIcuStays = icuStay.map(x => x.subjectID.toString + "," + x.hamdID.toString + "," + x.icuStayID + "," +DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(x.inTime) + "," + DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(x.outTime))
    val ret = header.union(csvIcuStays)
   
    ret.saveAsTextFile(tmp_file)
    merge(tmp_file, file)
  }

  def writeChartEvent(chartEvent: RDD[ChartEvent]) = {
    val tmp_file = "data_filter/TMP_CHARTEVENTS.csv"
    val file = "data_filter/CHARTEVENTS.csv"
    FileUtil.fullyDelete(new File(file))
    FileUtil.fullyDelete(new File(tmp_file))
    val sc = chartEvent.context
    val header = getChartEventHeader(sc)

    val csvChartEvent = chartEvent.map(x => x.subjectID.toString + "," + x.hamdID.toString + "," + x.icuStayID + "," + x.itemID.toString + ",\"" + x.value.toString + "\"," + x.numericValue.toString + ",\"" + x.unitMeasurement + "\"," + DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(x.chartTime))
    val ret = header.union(csvChartEvent)
   
    ret.saveAsTextFile(tmp_file)
    merge(tmp_file, file)
  }

  def writeDiagnose(diagnose: RDD[Diagnose]) = {
    val tmp_file = "data_filter/TMP_DIAGNOSES_ICD.csv"
    val file = "data_filter/DIAGNOSES_ICD.csv"
    FileUtil.fullyDelete(new File(file))
    FileUtil.fullyDelete(new File(tmp_file))
    val sc = diagnose.context
    val header = getDiagnoseHeader(sc)

    val csvDiagnose = diagnose.map(x => x.subjectID.toString + "," + x.hamdID.toString + ",\"" + x.icd9Code.toString + "\"," + x.seqNum.toString)
    val ret = header.union(csvDiagnose)
   
    ret.saveAsTextFile(tmp_file)
    merge(tmp_file, file)
  } 


  def getPatientHeader(sc : SparkContext) = {
     val header: RDD[String] = sc.parallelize(Array("\"SUBJECT_ID\",\"GENDER\",\"DOB\""))
    
     header
  }

  def getICUHeader(sc: SparkContext) = {
    val header: RDD[String] = sc.parallelize(Array("\"SUBJECT_ID\",\"HADM_ID\",\"ICUSTAY_ID\",\"INTIME\",\"OUTTIME\""))

    header
  }

def getChartEventHeader(sc: SparkContext) = {
    val header: RDD[String] = sc.parallelize(Array("\"SUBJECT_ID\",\"HADM_ID\",\"ICUSTAY_ID\",\"ITEMID\",\"VALUE\",\"VALUENUM\",\"VALUEUOM\",\"CHARTTIME\""))

    header
  }

def getDiagnoseHeader(sc: SparkContext) = {
  val header: RDD[String] = sc.parallelize(Array("\"SUBJECT_ID\",\"HADM_ID\",\"ICD9_CODE\",\"SEQ_NUM\""))
    header
}



}
