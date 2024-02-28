/*
 * Copyright 2024 HM Revenue & Customs
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
import cats.syntax.eq._
import cats.syntax.traverse._
import cats.instances.option._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import com.typesafe.config.Config
import configs.syntax._
import uk.gov.hmrc.hec.controllers.actions.AuthenticatedGGOrStrideRequest
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.AuditEvent.TaxCheckSuccess
import uk.gov.hmrc.hec.models.ids.GGCredId
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.hecTaxCheck.{HECTaxCheck, HECTaxCheckData}
import uk.gov.hmrc.hec.models.taxCheckMatch.{HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckMatchStatus, MatchFailureReason}
import uk.gov.hmrc.hec.models.{Error, SaveEmailAddressRequest, TaxCheckListItem}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.LicenceType
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.util.TimeProvider
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxCheckServiceImpl])
trait TaxCheckService {

  def saveTaxCheck(taxCheckData: HECTaxCheckData)(implicit
    hc: HeaderCarrier,
    request: AuthenticatedGGOrStrideRequest[_]
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

  def getAllTaxCheckCodesByExtractedStatus(isExtracted: Boolean, skip: Int, limit: Int, sortBy: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]]

  def getAllTaxCheckCodesByFileCorrelationId(fileCorrelationId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]]

  def saveEmailAddress(request: SaveEmailAddressRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Option[Unit]]

}

@Singleton
class TaxCheckServiceImpl @Inject() (
  taxCheckCodeGeneratorService: TaxCheckCodeGeneratorService,
  auditService: AuditService,
  timeProvider: TimeProvider,
  taxCheckStore: HECTaxCheckStore,
  config: Config
)(implicit ec: ExecutionContext)
    extends TaxCheckService {
  val key: String                        = "hec-tax-check"
  val taxCheckCodeExpiresAfterDays: Long =
    config.get[FiniteDuration]("hec-tax-check.expires-after").value.toDays

  def saveTaxCheck(
    taxCheckData: HECTaxCheckData
  )(implicit hc: HeaderCarrier, request: AuthenticatedGGOrStrideRequest[_]): EitherT[Future, Error, HECTaxCheck] = {
    val taxCheckCode = taxCheckCodeGeneratorService.generateTaxCheckCode()
    val expiryDate   = timeProvider.currentDate.plusDays(taxCheckCodeExpiresAfterDays)
    val createDate   = timeProvider.currentDateTime
    val taxCheck     =
      models.hecTaxCheck.HECTaxCheck(taxCheckData, taxCheckCode, expiryDate, createDate, false, None, None)

    taxCheckStore.store(taxCheck).map { _ =>
      auditService.sendEvent(TaxCheckSuccess(taxCheck, request.loginDetails.swap.toOption))
      taxCheck
    }
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
          HECTaxCheckMatchResult(
            taxCheckMatchRequest,
            timeProvider.currentDateTime,
            HECTaxCheckMatchStatus.NoMatch(MatchFailureReason.TaxCheckCodeNotMatched)
          )
        )(
          doMatch(taxCheckMatchRequest, _)
        )
      )

  private def doMatch(
    taxCheckMatchRequest: HECTaxCheckMatchRequest,
    storedTaxCheck: HECTaxCheck
  ): HECTaxCheckMatchResult = {
    lazy val hasExpired = timeProvider.currentDate.isAfter(storedTaxCheck.expiresAfter)

    val licenceTypeMatchFailure =
      if (licenceTypeMatches(taxCheckMatchRequest.licenceType, storedTaxCheck.taxCheckData.licenceDetails.licenceType))
        None
      else Some(MatchFailureReason.LicenceTypeNotMatched)

    val failureReason: Option[MatchFailureReason] = (taxCheckMatchRequest.verifier, storedTaxCheck.taxCheckData) match {
      case (Right(dateOfBirth), storedIndividualData: IndividualHECTaxCheckData) =>
        if (dateOfBirth === storedIndividualData.applicantDetails.dateOfBirth) licenceTypeMatchFailure
        else if (licenceTypeMatchFailure.isEmpty) Some(MatchFailureReason.DateOfBirthNotMatched)
        else Some(MatchFailureReason.LicenceTypeDateOfBirthNotMatched)

      case (Left(crn), storedCompanyData: CompanyHECTaxCheckData) =>
        if (crn === storedCompanyData.applicantDetails.crn) licenceTypeMatchFailure
        else if (licenceTypeMatchFailure.isEmpty) Some(MatchFailureReason.CRNNotMatched)
        else Some(MatchFailureReason.LicenceTypeCRNNotMatched)

      case _ =>
        if (licenceTypeMatchFailure.isEmpty) Some(MatchFailureReason.EntityTypeNotMatched)
        else Some(MatchFailureReason.LicenceTypeEntityTypeNotMatched)
    }

    failureReason.fold(
      if (hasExpired)
        HECTaxCheckMatchResult(taxCheckMatchRequest, timeProvider.currentDateTime, HECTaxCheckMatchStatus.Expired)
      else HECTaxCheckMatchResult(taxCheckMatchRequest, timeProvider.currentDateTime, HECTaxCheckMatchStatus.Match)
    ) { reason =>
      HECTaxCheckMatchResult(taxCheckMatchRequest, timeProvider.currentDateTime, HECTaxCheckMatchStatus.NoMatch(reason))
    }
  }

  private def licenceTypeMatches(submittedLicenceType: LicenceType, actualLicenceType: LicenceType): Boolean =
    (submittedLicenceType, actualLicenceType) match {
      case (LicenceType.BookingOffice, LicenceType.OperatorOfPrivateHireVehicles) => true
      case (LicenceType.OperatorOfPrivateHireVehicles, LicenceType.BookingOffice) => true
      case _                                                                      => submittedLicenceType === actualLicenceType
    }

  def getUnexpiredTaxCheckCodes(
    ggCredId: GGCredId
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, List[TaxCheckListItem]] = {
    val today = timeProvider.currentDate
    taxCheckStore
      .getTaxCheckCodes(ggCredId)
      .map(
        _.filterNot(item => item.expiresAfter.isBefore(today))
          .map(TaxCheckListItem.fromHecTaxCheck)
      )
  }

  override def getAllTaxCheckCodesByExtractedStatus(isExtracted: Boolean, skip: Int, limit: Int, sortBy: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]] =
    taxCheckStore.getAllTaxCheckCodesByExtractedStatus(isExtracted, skip, limit, sortBy)

  override def getAllTaxCheckCodesByFileCorrelationId(fileCorrelationId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]] =
    taxCheckStore.getAllTaxCheckCodesByFileCorrelationId(fileCorrelationId)

  override def saveEmailAddress(
    request: SaveEmailAddressRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[Unit]] =
    for {
      taxCheck       <- taxCheckStore.get(request.taxCheckCode)
      updatedTaxCheck = taxCheck.map(_.copy(latestTaxCheckEmailSentTo = Some(request.emailAddress)))
      result         <- updatedTaxCheck.map(taxCheckStore.store).sequence[EitherT[Future, Error, *], Unit]
    } yield result

}
