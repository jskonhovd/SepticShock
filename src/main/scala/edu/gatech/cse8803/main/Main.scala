/**
 * @author Skonhovd, Jeffrey
 */

package edu.gatech.cse8803.main

import java.text.SimpleDateFormat

import edu.gatech.cse8803.ioutils.CSVUtils
import edu.gatech.cse8803.output.WriteCSV
import edu.gatech.cse8803.model._
import edu.gatech.cse8803.cohort.CohortConstruction
import edu.gatech.cse8803.prediction.PredictionTarget
import edu.gatech.cse8803.features.FeatureConstruction

import org.apache.spark.SparkContext._
import org.apache.spark.mllib.clustering.{GaussianMixture, KMeans}
import org.apache.spark.mllib.linalg.{DenseMatrix, Matrices, Vectors, Vector}
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.util._

import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time._
import org.joda.time.format.DateTimeFormat
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.regression.LabeledPoint


import scala.io.Source


object Main {
  def main(args: Array[String]) {
    import org.apache.log4j.Logger
    import org.apache.log4j.Level

    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("akka").setLevel(Level.WARN)
    println("Start")

    val sc = createContext
    val sqlContext = new SQLContext(sc)

    /** initialize loading of data */
    val (patient, icuStay, chartEvent, diagnose) = loadRddRawData(sqlContext)
    val (cohortItemIds, sirsItemIds, sepsisItemIds, infectICD9, sepsisICD9, averageFeaturesConstructionItemIds) = loadLocalRawData
    println(patient.count())

    val (filteredPatient, filteredIcuStay, filteredChartEvent, filteredDiagnose) = cohort_design(patient, icuStay, chartEvent, diagnose, cohortItemIds)

    println(filteredChartEvent.count)
    println(filteredChartEvent.partitions.size)
    filteredChartEvent.cache()
    //println(SizeEstimator.estimate(filteredChartEvent))
    val (controlPatients, setSepsis,setSepsisOrgan, setSepticShock) = prediction_target(filteredPatient, filteredChartEvent, filteredDiagnose, sirsItemIds, sepsisItemIds, infectICD9, sepsisICD9)

    println(controlPatients.count)
    println(setSepsis.count)
    println(setSepsisOrgan.count)
    println(setSepticShock.count)

    val w1 = PredictionTarget.get_observation_window_control(filteredChartEvent, filteredIcuStay, controlPatients)
    val w2 = PredictionTarget.get_observation_window_septic_shock(filteredChartEvent, filteredIcuStay, filteredDiagnose, setSepticShock, sepsisItemIds, sepsisICD9)

    w1.cache()
    w2.cache()

    w1.take(4).foreach(println)
    println(w1.count)
    println(w2.count)

    val control_target = w1.map(x => (x._1, x._4))
    val shock_target = w2.map(x => (x._1, x._5))

    val control_window = w1.map(x => (x._1, (x._2, x._3))).distinct.collectAsMap
    val shock_window = w2.map(x => (x._1, (x._2, x._3))).distinct.collectAsMap

    val candidate_MAP = (r:Int) => averageFeaturesConstructionItemIds.contains(r)

    //control_window.foreach(println)
    val l = chartEvent.filter(x => candidate_MAP(x.itemID)).filter(x => !x.numericValue.isEmpty)
    //l.take(5).foreach(println)

    
    //println(control_window)
    //println(shock_window)
    l.cache()
    val controlFilterSet = controlPatients.collect().toSet[Int]
    val control_l = CohortConstruction.filterChartEvents(l, controlFilterSet)
    
    val control_features = FeatureConstruction.constructAverageChartFeatureTuple(control_l, control_window)

    control_features.take(5).foreach(println)

    val shockFilterSet = setSepticShock.collect().toSet[Int]

    val shock_l = CohortConstruction.filterChartEvents(l, shockFilterSet)

    val shock_features = FeatureConstruction.constructAverageChartFeatureTuple(shock_l, shock_window)


    //shock_features.take(10).foreach(println)

    //patient_id1 label feature_id:feature_value feature_id:feature_value feature_id:feature_value ...
    //patient_id2 label feature_id:feature_value feature_id:feature_value feature_id:feature_value ...  
    
    //MLUtils.saveAsLibSVMFile(finalSamples, "samples")
    val fv = control_features.union(shock_features)
    val features_vector = FeatureConstruction.construct(sc, fv)
    //val shock_features_vector = FeatureConstruction.construct(sc, shock_features)

    //features_vector.take(5).foreach(println)
    val target = control_target.union(shock_target)
    val final_sample = features_vector.join(target).map(x => (x._1, x._2._2, x._2._1))

    final_sample.take(5).foreach(println)
    FeatureConstruction.write_features(final_sample)
    sc.stop()

  }

  def prediction_target(patient: RDD[Patient], chartEvent: RDD[ChartEvent], diagnose: RDD[Diagnose], sirsItemIds: Set[Int], sepsisItemIds: Set[Int], infectICD9: Set[String], sepsisICD9: Set[String]) = {
    val setSepsis = PredictionTarget.setSepsis(chartEvent, diagnose, sirsItemIds, infectICD9)
    val setSepsisOrgan = PredictionTarget.setSepsisRelatedOrganDisfunction(chartEvent, diagnose, sepsisItemIds, sepsisICD9)

    val septicShock = setSepsis.intersection(setSepsisOrgan)
    val controlPatients = PredictionTarget.getControlPatients(patient, septicShock)

    (controlPatients, setSepsis, setSepsisOrgan, septicShock)
  }

  def cohort_design(patient: RDD[Patient], icuStay: RDD[ICUStay], chartEvent: RDD[ChartEvent], diagnose: RDD[Diagnose], cohortItemIds: Set[Int]) = {
    
    val cohortSubjectIds = CohortConstruction.construct(patient, icuStay, chartEvent, cohortItemIds)
    println(cohortSubjectIds.count())
    val filterSet = cohortSubjectIds.collect().toSet[Int]
    val filterPatientRDD = CohortConstruction.filterPatient(patient, filterSet)
    val filterICUStay = CohortConstruction.filterICUStay(icuStay, filterSet)
    val filterChartEvent = CohortConstruction.filterChartEvents(chartEvent, filterSet)
    val filterDiagnose = CohortConstruction.filterDiagnose(diagnose, filterSet)

    WriteCSV.writePatient(filterPatientRDD)
    WriteCSV.writeICUStay(filterICUStay)
    WriteCSV.writeChartEvent(filterChartEvent)
    WriteCSV.writeDiagnose(filterDiagnose)

    (filterPatientRDD, filterICUStay, filterChartEvent, filterDiagnose) 
  }

  /**
   * load the sets of string for filtering of medication
   * lab result and diagnostics
8    *
    * @return
   */
  def loadLocalRawData: (Set[Int], Set[Int], Set[Int], Set[String], Set[String], Set[Int]) = {
    val strCohortItemIds = Source.fromFile("filters/cohort_itemid.txt").getLines().map(_.toLowerCase).toSet[String]
    val strSIRSItemIds = Source.fromFile("filters/filter_sirs_itemid.txt").getLines().map(_.toLowerCase).toSet[String]

    val strSepsisItemIds = Source.fromFile("filters/filter_sepsis_organ_itemid.txt").getLines().map(_.toLowerCase).toSet[String]

    val strAverageFeaturesConstructionItemIds = Source.fromFile("filters/average_features_construction.txt").getLines().map(_.toLowerCase).toSet[String]


    val infectICD9 = Source.fromFile("filters/icd9_indicating_infection.txt").getLines().map(_.toLowerCase).toSet[String]
    val sepsisICD9 = Source.fromFile("filters/icd9_sepsis_organ.txt").getLines().map(_.toLowerCase).toSet[String]
    
    val cohortItemIds = strCohortItemIds.map(_.toInt)
    val sirsItemIds = strSIRSItemIds.map(_.toInt)
    val sepsisItemIds = strSepsisItemIds.map(_.toInt)
    val averageFeaturesConstructionItemIds = strAverageFeaturesConstructionItemIds.map(_.toInt)

    (cohortItemIds, sirsItemIds, sepsisItemIds, infectICD9, sepsisICD9, averageFeaturesConstructionItemIds)
  }


   def loadRddRawData(sqlContext: SQLContext): (RDD[Patient], RDD[ICUStay], RDD[ChartEvent], RDD[Diagnose]) = {

     val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
     //val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
     //val dir = "data_filter/"
     val dir = "data/"
    List(dir + "PATIENTS.csv", dir + "ICUSTAYS.csv", dir + "CHARTEVENTS.csv", dir + "DIAGNOSES_ICD.csv")
      .foreach(CSVUtils.loadCSVAsTable(sqlContext, _))

    val patient = sqlContext.sql(
      """
        |SELECT SUBJECT_ID, GENDER, DOB
        |FROM PATIENTS
        |WHERE DOB IS NOT NULL and DOB <> ''
      """.stripMargin).map(r => Patient(r(0).toString.toInt, r(1).toString, DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime(r(2).toString)))

    val icuStay = sqlContext.sql(
      """
        |SELECT SUBJECT_ID, HADM_ID, ICUSTAY_ID, INTIME, OUTTIME
        |FROM ICUSTAYS
        |WHERE INTIME IS NOT NULL and INTIME <> ''
        |AND OUTTIME IS NOT NULL and OUTTIME <> ''
      """.stripMargin)
      .map(r => ICUStay(r(0).toString.toInt, r(1).toString.toInt, r(2).toString.toInt, DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime(r(3).toString), DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime(r(4).toString)))

    val chartEvent = sqlContext.sql(
      """
        |SELECT SUBJECT_ID, HADM_ID, ICUSTAY_ID, ITEMID, VALUE, VALUENUM, VALUEUOM, CHARTTIME
        |FROM CHARTEVENTS
        |WHERE CHARTTIME IS NOT NULL and CHARTTIME <> ''
        |AND SUBJECT_ID IS NOT NULL and SUBJECT_ID <> ''
        |AND HADM_ID IS NOT NULL and HADM_ID <> ''
        |AND ICUSTAY_ID IS NOT NULL and ICUSTAY_ID <> ''
        |AND ITEMID IS NOT NULL and ITEMID <> ''
      """.stripMargin)
      .map(r => ChartEvent(r(0).toString.toInt,
        r(1).toString.toInt,
        r(2).toString.toInt,
        r(3).toString.toInt,
        r(4).toString,
        r(5).toString,
        r(6).toString,
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime(r(7).toString)))

     val diagnose = sqlContext.sql(
      """
        |SELECT SUBJECT_ID, HADM_ID, ICD9_CODE, SEQ_NUM
        |FROM DIAGNOSES_ICD
        |WHERE SUBJECT_ID IS NOT NULL and SUBJECT_ID <> ''
        |AND HADM_ID IS NOT NULL and HADM_ID <> ''
        |AND SEQ_NUM IS NOT NULL and SEQ_NUM <> ''
      """.stripMargin)
      .map(r => Diagnose(r(0).toString.toInt,
        r(1).toString.toInt,
        r(2).toString,
        r(3).toString.toInt))

    (patient, icuStay, chartEvent, diagnose)

  }


  def createContext(appName: String, masterUrl: String): SparkContext = {
    val conf = new SparkConf().setAppName(appName).setMaster(masterUrl).set("spark.default.parallelism", "10")
    //val conf = new SparkConf().setAppName(appName).setMaster(masterUrl).set("spark.sql.shuffle.partitions", "1")
    //val conf = new SparkConf().setAppName(appName).setMaster(masterUrl).set("spark.executor.memory", "2g").set("spark.driver.memory", "8g")
    new SparkContext(conf)
  }

  def createContext(appName: String): SparkContext = createContext(appName, "local[*]")
  def createContext: SparkContext = createContext("CSE 8803 Homework 3", "local[*]")
  //def createContext: SparkContext = createContext("CSE 8803 Homework Two Application", "local")
}
