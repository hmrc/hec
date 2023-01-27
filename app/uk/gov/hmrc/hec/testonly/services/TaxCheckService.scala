/*
 * Copyright 2023 HM Revenue & Customs
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
import cats.implicits.catsSyntaxOptionId
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.hecTaxCheck.ApplicantDetails.{CompanyApplicantDetails, IndividualApplicantDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxDetails.{CompanyTaxDetails, IndividualTaxDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTStatus, CTStatusResponse, CompanyHouseName}
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.Name
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.{LicenceDetails, LicenceTimeTrading, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.hecTaxCheck._
import uk.gov.hmrc.hec.models.hecTaxCheck.company.CTAccountingPeriod.CTAccountingPeriodDigital
import uk.gov.hmrc.hec.models.ids.{CTUTR, NINO}
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.testonly.models.SaveTaxCheckRequest
import uk.gov.hmrc.hec.util.TimeProvider
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
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
  taxCheckStore: HECTaxCheckStore,
  timeProvider: TimeProvider
) extends TaxCheckService {

  def saveTaxCheck(
    saveTaxCheckRequest: SaveTaxCheckRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] = {
    val taxCheck =
      models.hecTaxCheck.HECTaxCheck(
        taxCheckData(saveTaxCheckRequest),
        saveTaxCheckRequest.taxCheckCode,
        saveTaxCheckRequest.expiresAfter,
        saveTaxCheckRequest.createDate,
        false,
        None,
        None
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

    val ggCredId = saveTaxCheckRequest.ggCredId

    saveTaxCheckRequest.verifier match {
      case Left(crn) =>
        val companyDetails    = CompanyApplicantDetails(ggCredId.some, crn, CompanyHouseName("Test Tech Ltd"))
        val companyTaxDetails = CompanyTaxDetails(
          CTUTR("1111111111"),
          Some(CTUTR("1111111111")),
          Some(YesNoAnswer.Yes),
          CTStatusResponse(
            CTUTR("1111111111"),
            LocalDate.of(2020, 10, 9),
            LocalDate.of(2021, 10, 9),
            Some(CTAccountingPeriodDigital(LocalDate.of(2020, 10, 9), LocalDate.of(2021, 10, 9), CTStatus.ReturnFound))
          ),
          None,
          Some(YesNoAnswer.Yes),
          saveTaxCheckRequest.correctiveAction
        )
        CompanyHECTaxCheckData(
          companyDetails,
          licenceDetails,
          companyTaxDetails,
          saveTaxCheckRequest.taxCheckStartDateTime,
          saveTaxCheckRequest.source,
          None,
          Some(false),
          Some(false)
        )

      case Right(dob) =>
        val individualDetails    = IndividualApplicantDetails(Some(ggCredId), Name("TestFirst", "TestLast"), dob)
        val individualTaxDetails = IndividualTaxDetails(
          NINO("AB123456C"),
          None,
          TaxSituation.PAYE,
          None,
          None,
          saveTaxCheckRequest.relevantIncomeTaxYear.getOrElse(getTaxYear(timeProvider.currentDate)),
          saveTaxCheckRequest.correctiveAction
        )
        IndividualHECTaxCheckData(
          individualDetails,
          licenceDetails,
          individualTaxDetails,
          saveTaxCheckRequest.taxCheckStartDateTime,
          saveTaxCheckRequest.source,
          None,
          Some(false),
          Some(false)
        )
    }

  }

  private def getTaxYear(currentDate: LocalDate): TaxYear = {
    val currentYear             = currentDate.getYear
    val currentYearTaxStartDate = LocalDate.of(currentYear, 4, 6)
    val sixMonthEarlierDate     = currentDate.minusMonths(6L)
    if (sixMonthEarlierDate.isBefore(currentYearTaxStartDate)) TaxYear(currentYear - 2)
    else TaxYear(currentYear - 1)
  }

}
