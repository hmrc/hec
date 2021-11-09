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

package uk.gov.hmrc.hec.services

import cats.data.EitherT
import cats.implicits._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import com.typesafe.config.Config
import configs.syntax._
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.ids.GGCredId
import uk.gov.hmrc.hec.models.{Error, HECTaxCheck, HECTaxCheckData, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckMatchStatus, TaxCheckListItem}
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.util.TimeProvider
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxCheckServiceImpl])
trait TaxCheckService {

  def saveTaxCheck(taxCheckData: HECTaxCheckData)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HECTaxCheck]

  def updateAllHecTaxCheck(list: List[HECTaxCheck])(implicit
    hc: HeaderCarrier
  ): EitherT[Future, models.Error, List[HECTaxCheck]]

  def matchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HECTaxCheckMatchResult]

  def getUnexpiredTaxCheckCodes(ggCredId: GGCredId)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[TaxCheckListItem]]

  def getAllTaxCheckCodesByExtractedStatus(isExtracted: Boolean)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]]

  def getAllTaxCheckCodesByCorrelationId(fileCorrelationId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]]

}

@Singleton
class TaxCheckServiceImpl @Inject() (
  taxCheckCodeGeneratorService: TaxCheckCodeGeneratorService,
  timeProvider: TimeProvider,
  taxCheckStore: HECTaxCheckStore,
  config: Config
)(implicit ec: ExecutionContext)
    extends TaxCheckService {
  val key: String                            = "hec-tax-check"
  private val ggCredIdField: String          = s"data.$key.taxCheckData.applicantDetails.ggCredId"
  private val isExtractedField: String       = s"data.$key.isExtracted"
  private val fileCorrelationIdField: String = s"data.$key.fileCorrelationId"
  val taxCheckCodeExpiresAfterDays: Long     =
    config.get[FiniteDuration]("hec-tax-check.expires-after").value.toDays

  def saveTaxCheck(
    taxCheckData: HECTaxCheckData
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HECTaxCheck] = {
    val taxCheckCode = taxCheckCodeGeneratorService.generateTaxCheckCode()
    val expiryDate   = timeProvider.currentDate.plusDays(taxCheckCodeExpiresAfterDays)
    val createDate   = timeProvider.currentDateTime
    val taxCheck     =
      HECTaxCheck(taxCheckData, taxCheckCode, expiryDate, createDate, false, None, None)

    taxCheckStore.store(taxCheck).map(_ => taxCheck)
  }

  private def updateHecTaxCheck(
    updatedHecTaxCheck: HECTaxCheck
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HECTaxCheck] =
    taxCheckStore.store(updatedHecTaxCheck).map(_ => updatedHecTaxCheck)

  def updateAllHecTaxCheck(list: List[HECTaxCheck])(implicit
    hc: HeaderCarrier
  ): EitherT[Future, models.Error, List[HECTaxCheck]] =
    list.traverse[EitherT[Future, models.Error, *], HECTaxCheck](updateHecTaxCheck)

  def matchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HECTaxCheckMatchResult] =
    taxCheckStore
      .get(taxCheckMatchRequest.taxCheckCode)
      .map(
        _.fold[HECTaxCheckMatchResult](
          HECTaxCheckMatchResult(taxCheckMatchRequest, timeProvider.currentDateTime, HECTaxCheckMatchStatus.NoMatch)
        )(
          doMatch(taxCheckMatchRequest, _)
        )
      )

  private def doMatch(
    taxCheckMatchRequest: HECTaxCheckMatchRequest,
    storedTaxCheck: HECTaxCheck
  ): HECTaxCheckMatchResult = {
    lazy val hasExpired = timeProvider.currentDate.isAfter(storedTaxCheck.expiresAfter)

    val applicantVerifierMatches = (taxCheckMatchRequest.verifier, storedTaxCheck.taxCheckData) match {
      case (Right(dateOfBirth), storedIndividualData: IndividualHECTaxCheckData) =>
        dateOfBirth === storedIndividualData.applicantDetails.dateOfBirth

      case (Left(crn), storedCompanyData: CompanyHECTaxCheckData) =>
        crn === storedCompanyData.applicantDetails.crn

      case _ =>
        false
    }

    val licenceTypeMatches =
      taxCheckMatchRequest.licenceType === storedTaxCheck.taxCheckData.licenceDetails.licenceType

    if (licenceTypeMatches && applicantVerifierMatches) {
      if (hasExpired)
        HECTaxCheckMatchResult(taxCheckMatchRequest, timeProvider.currentDateTime, HECTaxCheckMatchStatus.Expired)
      else HECTaxCheckMatchResult(taxCheckMatchRequest, timeProvider.currentDateTime, HECTaxCheckMatchStatus.Match)
    } else
      HECTaxCheckMatchResult(taxCheckMatchRequest, timeProvider.currentDateTime, HECTaxCheckMatchStatus.NoMatch)
  }

  def getUnexpiredTaxCheckCodes(
    ggCredId: GGCredId
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, List[TaxCheckListItem]] = {
    val today = timeProvider.currentDate
    taxCheckStore
      .getTaxCheckCodeByKey(ggCredIdField, ggCredId)
      .map(
        _.filterNot(item => item.expiresAfter.isBefore(today))
          .map(TaxCheckListItem.fromHecTaxCheck)
      )
  }

  override def getAllTaxCheckCodesByExtractedStatus(isExtracted: Boolean)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]] =
    taxCheckStore.getTaxCheckCodeByKey[Boolean](isExtractedField, isExtracted)

  override def getAllTaxCheckCodesByCorrelationId(fileCorrelationId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]] =
    taxCheckStore.getTaxCheckCodeByKey[String](fileCorrelationIdField, fileCorrelationId)
}
