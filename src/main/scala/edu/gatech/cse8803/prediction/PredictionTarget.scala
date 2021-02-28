package edu.gatech.cse8803.prediction

import java.io.File

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
import edu.gatech.cse8803.cohort.CohortConstruction
import org.apache.spark.util.SizeEstimator

object Joda {
    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
}
import Joda._


object PredictionTarget {

  def setSIRS(chartEvent: RDD[ChartEvent], candiateItemIds: Set[Int]): RDD[Int] = {
    val sc = chartEvent.context
    val test_set = Array(618).toSet

    val candiate_test_set = (r: Int) => test_set.contains(r)
    val candiateItemIds_MAP = (r: Int) => candiateItemIds.contains(r)

    val match_temp = (x: Int, y: Option[Double]) => (x,y) match {
      case (_, None) => false
      case (676, Some(v)) => (v <= 30) || (v >= 38)
      case (677, Some(v)) => (v <= 30) || (v >= 38)
      case (678, Some(v)) => (v <= 96.8) || (v >= 100.4)
      case (679, Some(v)) => (v <= 96.8) || (v >= 100.4)
      case (223761, Some(v)) => (v <= 96.8) || (v >= 100.4)
      case (223762, Some(v)) => (v <= 30) || (v >= 38)
      case _ => false
    }
    val match_heart = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (211, Some(v)) => (v >= 90.0)
      case (220045, Some(v)) => (v >= 90.0)
      case _ => false
    }

    val match_rep = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (618, Some(v)) => (v >= 20.0)
      case (619, Some(v)) => (v >= 20.0)
      case (224688, Some(v)) => (v >= 20.0)
      case (224689, Some(v)) => (v >= 20.0)
      case (224690, Some(v)) => (v >= 20.0)
      case (220210, Some(v)) => (v >= 20.0)
      case _ => false
    }

    val match_wbc = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (1127, Some(v)) => (v <= 4.0) || (v >= 12.0)
      case (861, Some(v)) => (v <= 4.0) || (v >= 12.0)
      case (1542, Some(v)) => (v <= 4.0) || (v >= 12.0)
      case (220546, Some(v)) => (v <= 4.0) || (v >= 12.0)
      case _ => false
    }


    val fChartEvent = chartEvent.filter(x => candiateItemIds_MAP(x.itemID))

    fChartEvent.cache()
    //fChartEvent.take(10).foreach(println)

    val temp = fChartEvent.filter(x => match_temp(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble))).map(x => x.subjectID).distinct()

    val heart = fChartEvent.filter(x => match_heart(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble))).map(x => x.subjectID).distinct()

    val rep = fChartEvent.filter(x => match_rep(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble))).map(x => x.subjectID).distinct()

    val wbc = fChartEvent.filter(x => match_wbc(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble))).map(x => x.subjectID).distinct()

    val s1 = temp.intersection(heart)
    val s2 = temp.intersection(rep)
    val s3 = temp.intersection(wbc)
    val s4 = heart.intersection(rep)
    val s5 = heart.intersection(wbc)
    val s6 = rep.intersection(wbc)
    //heart.take(10).foreach(println)
    val ret = s1.union(s2).union(s3).union(s4).union(s5).union(s6).distinct()
    ret
  }

  def setInfectICD9(diagnose: RDD[Diagnose], candiateICD9: Set[String]): RDD[Int] = {
    val candiateIDC_MAP = (r: String) => candiateICD9.contains(r)

    val fDiagnose = diagnose.filter(x => candiateIDC_MAP(x.icd9Code))

    val ret = fDiagnose.map(x => x.subjectID).distinct()


    ret
  }

  def setSepsis(chartEvent: RDD[ChartEvent], diagnose: RDD[Diagnose], sirsItemIds: Set[Int], candiateICD9: Set[String]) = {
    val setSIRS = PredictionTarget.setSIRS(chartEvent, sirsItemIds)
    val setInfectICD9 = PredictionTarget.setInfectICD9(diagnose, candiateICD9)

    val ret = setSIRS.intersection(setInfectICD9)

    ret

  }

  def setSepsisRelatedOrganDisfunction(chartEvent: RDD[ChartEvent], diagnose: RDD[Diagnose], sepsisItemIds: Set[Int], sepsisICD9: Set[String]) = {
    val candiateIDC_MAP = (r: String) => sepsisICD9.contains(r)

    val candiateItemIds_MAP = (r: Int) => sepsisItemIds.contains(r)

    val match_sbp = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (6, Some(v)) => (v <= 90.0)
      case (51, Some(v)) => (v <= 90.0)
      case (442, Some(v)) => (v <= 90.0)
      case (3313, Some(v)) => (v <= 90.0)
      case (224167, Some(v)) => (v <= 90.0)
      case (227243, Some(v)) => (v <= 90.0)
      case (220050, Some(v)) => (v <= 90.0)
      case _ => false
    }

    val match_lactate = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (818, Some(v)) => (v >= 2.0)
      case (1531, Some(v)) => (v >= 2.0)
      case (225668, Some(v)) => (v >= 2.0)
      case _ => false
    }

    val match_creatinine = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (791, Some(v)) => (v >= 2.0)
      case (5811, Some(v)) => (v >= 2.0)
      case (3750, Some(v)) => (v >= 2.0)
      case (1525, Some(v)) => (v >= 2.0)
      case (220615, Some(v)) => (v >= 2.0)
      case _ => false
    }

    val match_bilirubin = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (4948, Some(v)) => (v >= 2.0)
      case (225651, Some(v)) => (v >= 2.0)
      case (225690, Some(v)) => (v >= 2.0)
      case _ => false
    }

    val match_inr = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (815, Some(v)) => (v >= 1.5)
      case (1530, Some(v)) => (v >= 1.5)
      case (227467, Some(v)) => (v >= 1.5)
      case (220561, Some(v)) => (v >= 1.5)
      case _ => false
}

    val fChartEvent = chartEvent.filter(x => candiateItemIds_MAP(x.itemID))
    fChartEvent.cache()
    val fDiagnose = diagnose.filter(x => candiateIDC_MAP(x.icd9Code))

    val sbp = fChartEvent.filter(x => match_sbp(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble))).map(x => x.subjectID).distinct()

    val lactate = fChartEvent.filter(x => match_lactate(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble))).map(x => x.subjectID).distinct()

    val creatinine = fChartEvent.filter(x => match_creatinine(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble))).map(x => x.subjectID).distinct()

    val creatinineICD9_SET = Set("V4511","5859")

    val creatinine_MAP = (r: String) => creatinineICD9_SET.contains(r)
    val diagCreatinine = fDiagnose.filter(x => creatinine_MAP(x.icd9Code)).map(x => x.subjectID).distinct()
    val set_creatinine_with_renal_fail = creatinine.intersection(diagCreatinine)

    val set_creatinine = creatinine.subtract(set_creatinine_with_renal_fail)

    val bilirubin = fChartEvent.filter(x => match_bilirubin(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble))).map(x => x.subjectID).distinct()

    val bilirubinICD9_SET = Set("571")
    val bilirubin_MAP = (r: String) => bilirubinICD9_SET.contains(r)
    val diagBilirubin = fDiagnose.filter(x => bilirubin_MAP(x.icd9Code)).map(x => x.subjectID).distinct()
    val set_bilirubin_with_liver_fail = bilirubin.intersection(diagBilirubin)

    val set_bilirubin = bilirubin.subtract(set_bilirubin_with_liver_fail)

    val inr = fChartEvent.filter(x => match_inr(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble))).map(x => x.subjectID).distinct()

    val s1 = sbp.union(lactate).union(set_creatinine).union(set_bilirubin).union(inr)

    val ret = s1.distinct()

    ret

  }

  def getControlPatients(patient: RDD[Patient], setSepticShock: RDD[Int]) =
  {
    val total = patient.map(x => x.subjectID).distinct()
    //println(total.count)
    val ret = total.subtract(setSepticShock)

    ret
  }


  def get_observation_window_control(chartEvent: RDD[ChartEvent], icuStay: RDD[ICUStay], setControlPatient: RDD[Int]) =
  {
    val filterSet = setControlPatient.collect().toSet[Int]
    val filterICU = CohortConstruction.filterICUStay(icuStay, filterSet)

    val w = filterICU.groupBy(x => x.subjectID).map(v => (v._1, v._2.minBy(x => x.inTime)))
    //val filterChartEvent = CohortConstruction.filterChartEvents(chartEvent, filterSet)

    val ret = w.map(x => (x._1, x._2.inTime, x._2.outTime, 0))

    ret

  }

  def get_observation_window_septic_shock(chartEvent: RDD[ChartEvent], icuStay: RDD[ICUStay], diagnose: RDD[Diagnose], setSepticShock: RDD[Int], sepsisItemIds: Set[Int], sepsisICD9: Set[String]) =
  {
    val filterSet = setSepticShock.collect().toSet[Int]
    val filterChartEvent = CohortConstruction.filterChartEvents(chartEvent, filterSet)

    val candiateItemIds_MAP = (r: Int) => sepsisItemIds.contains(r)
    val candiateIDC_MAP = (r: String) => sepsisICD9.contains(r)
    
    val match_sbp = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (6, Some(v)) => (v <= 90.0)
      case (51, Some(v)) => (v <= 90.0)
      case (442, Some(v)) => (v <= 90.0)
      case (3313, Some(v)) => (v <= 90.0)
      case (224167, Some(v)) => (v <= 90.0)
      case (227243, Some(v)) => (v <= 90.0)
      case (220050, Some(v)) => (v <= 90.0)
      case _ => false
    }

    val match_lactate = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (818, Some(v)) => (v >= 2.0)
      case (1531, Some(v)) => (v >= 2.0)
      case (225668, Some(v)) => (v >= 2.0)
      case _ => false
    }

    val match_creatinine = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (791, Some(v)) => (v >= 2.0)
      case (5811, Some(v)) => (v >= 2.0)
      case (3750, Some(v)) => (v >= 2.0)
      case (1525, Some(v)) => (v >= 2.0)
      case (220615, Some(v)) => (v >= 2.0)
      case _ => false
    }

    val match_bilirubin = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (4948, Some(v)) => (v >= 2.0)
      case (225651, Some(v)) => (v >= 2.0)
      case (225690, Some(v)) => (v >= 2.0)
      case _ => false
    }

    val match_inr = (x: Int, y: Option[Double]) => (x,y) match { 
      case (_, None) => false
      case (815, Some(v)) => (v >= 1.5)
      case (1530, Some(v)) => (v >= 1.5)
      case (227467, Some(v)) => (v >= 1.5)
      case (220561, Some(v)) => (v >= 1.5)
      case _ => false
    }

    val fChartEvent = filterChartEvent.filter(x => candiateItemIds_MAP(x.itemID))
    fChartEvent.cache()
    val fDiagnose = diagnose.filter(x => candiateIDC_MAP(x.icd9Code))

    val sbp = fChartEvent.filter(x => match_sbp(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble)))

    val lactate = fChartEvent.filter(x => match_lactate(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble)))

    val creatinine = fChartEvent.filter(x => match_creatinine(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble)))

    val bilirubin = fChartEvent.filter(x => match_bilirubin(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble)))

    val inr = fChartEvent.filter(x => match_inr(x.itemID, if (x.numericValue.isEmpty) None else Some(x.numericValue.toDouble)))

    //sbp.take(100).foreach(println)

    val chart_organ_damage = sbp.union(lactate).union(creatinine).union(bilirubin).union(inr)

    

    val filterICU = CohortConstruction.filterICUStay(icuStay, filterSet)

    val w1 = filterICU.groupBy(x => x.subjectID).map(v => (v._1, v._2.minBy(x => x.inTime)))
    val w2 = w1.map(x => (x._1, x._2.inTime))

    //val filterChartEvent = CohortConstruction.filterChartEvents(chartEvent, filterSet)

    //w2.take(30).foreach(println)

    val date1 = chart_organ_damage.groupBy(x => x.subjectID).map(v => (v._1, v._2.minBy(x => x.chartTime).chartTime))
    //date1.take(30).foreach(println)
    val ob_window = w2.join(date1)

    

    //val ob_window = date1.join(w2)

    //val w3 = ob_window.map(x => (x._1, x._2._1, x._2._2.minusHours(24), Hours.hoursBetween(x._2._1, x._2._2).getHours(), 1))
    val w3 = ob_window.map(x => (x._1, x._2._1, x._2._2.minusHours(24), Hours.hoursBetween(x._2._1, x._2._2.minusHours(24)).getHours(), 1))
    //val w = w2.join(w2)

    
    w3.take(30).foreach(println)

    w3
  }


}
