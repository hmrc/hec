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

import java.time.LocalDate
import java.util.UUID
import cats.data.EitherT
import cats.implicits._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.hec.connectors.IFConnector
import uk.gov.hmrc.hec.models.ids.{CTUTR, SAUTR}
import uk.gov.hmrc.hec.models.{CTAccountingPeriod, CTLookupStatus, CTStatus, CTStatusResponse, Error, SAStatus, SAStatusResponse, TaxYear}
import uk.gov.hmrc.hec.services.IFService.{BackendError, DataNotFoundError, IFError}
import uk.gov.hmrc.hec.services.IFServiceImpl.{RawAccountingPeriod, RawCTSuccessResponse, RawFailureResponse, RawSASuccessResponse}
import uk.gov.hmrc.hec.util.HttpResponseOps._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IFServiceImpl])
trait IFService {

  def getSAStatus(utr: SAUTR, taxYear: TaxYear, correlationId: UUID)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, IFError, SAStatusResponse]

  def getCTStatus(utr: CTUTR, startDate: LocalDate, endDate: LocalDate, correlationId: UUID)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, IFError, CTStatusResponse]

}

object IFService {
  sealed trait IFError extends Product with Serializable

  final case class DataNotFoundError(msg: String) extends IFError

  final case class BackendError(error: Error) extends IFError
}

@Singleton
class IFServiceImpl @Inject() (
  IFConnector: IFConnector
)(implicit ec: ExecutionContext)
    extends IFService {

  private def handleErrorPath(httpResponse: HttpResponse, utrType: String) = {
    val responseError = s"Response to get $utrType status came back with status ${httpResponse.status}"
    httpResponse.parseJSON[RawFailureResponse] match {
      case Left(_)         => Left(BackendError(Error(s"$responseError; could not parse body")))
      case Right(failures) =>
        val errorMsg = s"$responseError - ${failures.failures}"
        if (httpResponse.status === NOT_FOUND && failures.failures.exists(_.code === "NO_DATA_FOUND")) {
          Left(DataNotFoundError(errorMsg))
        } else {
          Left(BackendError(Error(errorMsg)))
        }
    }
  }

  override def getSAStatus(
    utr: SAUTR,
    taxYear: TaxYear,
    correlationId: UUID
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, IFError, SAStatusResponse] =
    IFConnector
      .getSAStatus(utr, taxYear, correlationId)
      .leftMap(BackendError)
      .subflatMap { httpResponse =>
        if (httpResponse.status === OK) {
          httpResponse
            .parseJSON[RawSASuccessResponse]
            .leftMap(e => BackendError(Error(e)))
            .flatMap(response =>
              Either
                .fromOption(
                  SAStatus.fromString(response.returnStatus),
                  BackendError(Error(s"Could not parse success return status ${response.returnStatus}"))
                )
                .map(SAStatusResponse(utr, taxYear, _))
            )
        } else {
          handleErrorPath(httpResponse, utrType = "SA")
        }
      }

  override def getCTStatus(
    utr: CTUTR,
    startDate: LocalDate,
    endDate: LocalDate,
    correlationId: UUID
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, IFError, CTStatusResponse] =
    IFConnector
      .getCTStatus(utr, startDate, endDate, correlationId)
      .leftMap(BackendError)
      .subflatMap { httpResponse =>
        if (httpResponse.status === OK) {
          httpResponse
            .parseJSON[RawCTSuccessResponse]
            .leftMap(e => BackendError(Error(e)))
            .flatMap(ctAccountingPeriodsValidation(_).map { accountingPeriods =>
              CTStatusResponse(utr, startDate, endDate, latestAccountingPeriod(accountingPeriods))
            })
        } else {
          handleErrorPath(httpResponse, utrType = "CT")
        }
      }

  private def latestAccountingPeriod(accountingPeriods: List[CTAccountingPeriod]): Option[CTAccountingPeriod] =
    accountingPeriods.sortWith { case (a1, a2) => a1.endDate.isAfter(a2.endDate) }.headOption

  private def ctAccountingPeriodsValidation(
    response: RawCTSuccessResponse
  ): Either[BackendError, List[CTAccountingPeriod]]                                                           =
    toCtLookupStatus(response).flatMap {
      case CTLookupStatus.NoLiveRecords =>
        Right(List.empty)

      case CTLookupStatus.Successful =>
        response.accountingPeriods
          .getOrElse(List.empty[RawAccountingPeriod])
          .traverse[Either[BackendError, *], CTAccountingPeriod](a =>
            toCtStatus(a)
              .map(status => CTAccountingPeriod(a.accountingPeriodStartDate, a.accountingPeriodEndDate, status))
          )
          .filterOrElse(
            _.nonEmpty,
            BackendError(Error("Could not find accounting periods for outer return status '0 = successful'"))
          )
    }

  private def toCtLookupStatus(response: RawCTSuccessResponse): Either[BackendError, CTLookupStatus] =
    response.returnStatus match {
      case "0"   => Right(CTLookupStatus.Successful)
      case "2"   => Right(CTLookupStatus.NoLiveRecords)
      case other => Left(BackendError(Error(s"Could not parse returnStatus $other")))
    }

  private def toCtStatus(rawAccountingPeriod: RawAccountingPeriod): Either[BackendError, CTStatus] =
    rawAccountingPeriod.accountingPeriodStatus match {
      case "1"   => Right(CTStatus.ReturnFound)
      case "2"   => Right(CTStatus.NoticeToFileIssued)
      case "3"   => Right(CTStatus.NoReturnFound)
      case other => Left(BackendError(Error(s"Could not parse accounting period status $other")))
    }
}

object IFServiceImpl {

  final case class RawSASuccessResponse(
    returnStatus: String
  )

  implicit val rawSASuccessReads: Reads[RawSASuccessResponse] = Json.reads

  final case class RawAccountingPeriod(
    accountingPeriodStartDate: LocalDate,
    accountingPeriodEndDate: LocalDate,
    accountingPeriodStatus: String
  )

  implicit val rawAccountingPeriodReads: Reads[RawAccountingPeriod] = Json.reads

  final case class RawCTSuccessResponse(
    returnStatus: String,
    accountingPeriods: Option[List[RawAccountingPeriod]]
  )

  implicit val rawCTSuccessReads: Reads[RawCTSuccessResponse] = Json.reads

  final case class RawFailure(
    code: String,
    reason: String
  )

  implicit val rawFailureResponseFailureReads: Reads[RawFailure] = Json.reads

  final case class RawFailureResponse(
    failures: List[RawFailure]
  )

  implicit val rawFailureResponseReads: Reads[RawFailureResponse] = Json.reads
}
