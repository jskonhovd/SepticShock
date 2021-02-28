/**
 * @author Skonhovd, Jeffrey
 */

package edu.gatech.cse8803.model

import org.joda.time._

case class Patient(subjectID: Int, gender: String, dob: DateTime)

case class ICUStay(subjectID: Int, hamdID: Int, icuStayID: Int, inTime: DateTime, outTime: DateTime)

case class ChartEvent(subjectID: Int, hamdID: Int, icuStayID: Int, itemID: Int, value: String, numericValue: String, unitMeasurement: String, chartTime: DateTime)

case class Diagnose(subjectID: Int, hamdID: Int, icd9Code: String, seqNum: Int)

case class Note(subjectID: Int, hamdID: Int, chartDate: DateTime, text: String)

