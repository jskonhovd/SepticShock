/**
 * @author Skonhovd, Jeffrey 
 */
package edu.gatech.cse8803.cohort

import edu.gatech.cse8803.model._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.apache.spark.mllib.regression.LabeledPoint
import org.joda.time._



object CohortConstruction {


 /**
   
 */ 
  def constructChartPatientTuple(chartEvent: RDD[ChartEvent], candiateItemIds: Set[Int]): RDD[Int] = {

    val candiateItemIds_MAP = (r: Int) => candiateItemIds.contains(r)

    val ret = chartEvent.filter(x => candiateItemIds_MAP(x.itemID)).map(x => x.subjectID).distinct()

    ret
  }

  def constructPatientValidAgeTuple(patient: RDD[Patient], icuStay: RDD[ICUStay]): RDD[Int] = {
    val pat = patient.map(x => (x.subjectID, x))
    val icu = icuStay.map(x => (x.subjectID, x))

    val d1 = pat.join(icu)
    val d2 = d1.filter(x => (Years.yearsBetween(x._2._1.dob, x._2._2.inTime).getYears() > 14))

    val olderThan15Years = d2.map(x => x._1).distinct()


    val ret = olderThan15Years

    ret
  }

  def construct(patient: RDD[Patient], icuStay: RDD[ICUStay], chartEvent: RDD[ChartEvent], candiateItemIds: Set[Int]): RDD[Int] = {

    val d1 = constructChartPatientTuple(chartEvent, candiateItemIds)
    val d2 = constructPatientValidAgeTuple(patient, icuStay)

    val ret = d1.intersection(d2)

    ret
  }

  def filterPatient(patient: RDD[Patient], filterSet: Set[Int]): RDD[Patient] = {
    val candiateFilterSet_MAP = (r: Int) => filterSet.contains(r)
    val ret = patient.filter(x => candiateFilterSet_MAP(x.subjectID))

    ret
  }

  def filterICUStay(icuStay: RDD[ICUStay], filterSet: Set[Int]): RDD[ICUStay] = {
    val candiateFilterSet_MAP = (r: Int) => filterSet.contains(r)
    val ret = icuStay.filter(x => candiateFilterSet_MAP(x.subjectID))

    ret

  }

  def filterChartEvents(chartEvent: RDD[ChartEvent], filterSet: Set[Int]): RDD[ChartEvent] = {

    val candiateFilterSet_MAP = (r: Int) => filterSet.contains(r)

    val ret = chartEvent.filter(x => candiateFilterSet_MAP(x.subjectID))

    ret
  }

  def filterDiagnose(diagnose: RDD[Diagnose], filterSet: Set[Int]) = {
    val candiateFilterSet_MAP = (r: Int) => filterSet.contains(r)
    val ret = diagnose.filter(x => candiateFilterSet_MAP(x.subjectID))
    ret
  }




}


