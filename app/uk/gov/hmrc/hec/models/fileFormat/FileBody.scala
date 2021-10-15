/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.hec.models.fileFormat

trait FileBody extends Product with Serializable {
  def recordType: String
  def toRowString: String
}

//file body for enum file
final case class EnumFileBody(recordType: String = "01", recordId: String, recordDescription: String) extends FileBody {
  //convert the file body to pipe delimited string
  override def toRowString: String = this.productIterator.mkString("|")

}

final case class HECTaxCheckFileBody(
  recordType: String = "01",
  ggCredID: Option[String] = None,
  nino: Option[String] = None,
  firstName: Option[String] = None,
  lastName: Option[String] = None,
  dob: Option[String] = None,
  SAUTR: Option[Int] = None,
  CTUTR: Option[String] = None,
  crn: Option[String] = None,
  companyName: Option[String] = None,
  licenceType: String,
  licenceValidityPeriod: String,
  licenceTimeTrading: String,
  entityType: Char,
  notChargeable: Option[Char] = None,
  PAYE: Option[Char] = None,
  SA: Option[Char] = None,
  CT: Option[Char] = None,
  incomeTaxYear: Option[Int] = None,
  hasAccountingPeriod: Option[Char] = None,
  accountingPeriodStartDate: Option[String] = None,
  accountingPeriodEndDate: Option[String] = None,
  recentlyStartedTrading: Option[Char] = None,
  returnReceived: Option[Char] = None,
  noticeToFile: Option[Char] = None,
  taxComplianceDeclaration: Option[Char] = None,
  correctiveAction: Option[String] = None,
  customerDeclaration: Char,
  taxCheckStartDateTime: String,
  taxCheckCompleteDateTime: String,
  taxCheckCode: String,
  taxCheckExpiryDate: String,
  onlineApplication: Char
) extends FileBody {

  override def toRowString: String = this.productIterator.mkString("|").replaceAll("None", "null")

}
