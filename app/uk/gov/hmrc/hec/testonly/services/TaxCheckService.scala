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

package uk.gov.hmrc.hec.testonly.services

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.hec.models.ApplicantDetails.{CompanyApplicantDetails, IndividualApplicantDetails}
import uk.gov.hmrc.hec.models.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.TaxDetails.{CompanyTaxDetails, IndividualTaxDetails}
import uk.gov.hmrc.hec.models.ids.{CTUTR, GGCredId, NINO}
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceTimeTrading, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.{Error, HECTaxCheck, HECTaxCheckCode, HECTaxCheckData, Name, TaxSituation}
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.testonly.models.SaveTaxCheckRequest
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

@ImplementedBy(classOf[TaxCheckServiceImpl])
trait TaxCheckService {

  def saveTaxCheck(saveTaxCheckRequest: SaveTaxCheckRequest)(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit]

  def getTaxCheck(taxCheckCode: HECTaxCheckCode)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Option[HECTaxCheck]]

  def deleteTaxCheck(taxCheckCode: HECTaxCheckCode)(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit]

  def deleteAllTaxCheck()(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit]

}

@Singleton
class TaxCheckServiceImpl @Inject() (
  taxCheckStore: HECTaxCheckStore
) extends TaxCheckService {

  def saveTaxCheck(
    saveTaxCheckRequest: SaveTaxCheckRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] = {
    val taxCheck =
      HECTaxCheck(
        taxCheckData(saveTaxCheckRequest),
        saveTaxCheckRequest.taxCheckCode,
        saveTaxCheckRequest.expiresAfter,
        saveTaxCheckRequest.createDate,
        false
      )

    taxCheckStore.store(taxCheck)
  }

  def getTaxCheck(taxCheckCode: HECTaxCheckCode)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Option[HECTaxCheck]] =
    taxCheckStore.get(taxCheckCode)

  def deleteTaxCheck(taxCheckCode: HECTaxCheckCode)(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] =
    taxCheckStore.delete(taxCheckCode)

  def deleteAllTaxCheck()(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] =
    taxCheckStore.deleteAll()

  private[services] def taxCheckData(
    saveTaxCheckRequest: SaveTaxCheckRequest
  ): HECTaxCheckData = {
    val licenceDetails = LicenceDetails(
      saveTaxCheckRequest.licenceType,
      LicenceTimeTrading.ZeroToTwoYears,
      LicenceValidityPeriod.UpToOneYear
    )

    val ggCredId = GGCredId("testCredId")

    saveTaxCheckRequest.verifier match {
      case Left(crn) =>
        val companyDetails    = CompanyApplicantDetails(ggCredId, crn)
        val companyTaxDetails = CompanyTaxDetails(CTUTR("1234567895"))
        CompanyHECTaxCheckData(companyDetails, licenceDetails, companyTaxDetails)

      case Right(dob) =>
        val individualDetails    = IndividualApplicantDetails(ggCredId, Name("TestFirst", "TestLast"), dob)
        val individualTaxDetails = IndividualTaxDetails(NINO("AB123456C"), None, TaxSituation.PAYE, None)
        IndividualHECTaxCheckData(individualDetails, licenceDetails, individualTaxDetails)
    }

  }

}
