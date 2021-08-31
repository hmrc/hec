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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.OK
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.hec.connectors.IFConnector
import uk.gov.hmrc.hec.models.ids.{CTUTR, SAUTR}
import uk.gov.hmrc.hec.models.{AccountingPeriod, CTStatus, CTStatusResponse, Error, SAStatus, SAStatusResponse, TaxYear}
import uk.gov.hmrc.hec.services.IFServiceImpl.{RawCTSuccessResponse, RawFailureResponse, RawSASuccessResponse}
import uk.gov.hmrc.hec.util.HttpResponseOps._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IFServiceImpl])
trait IFService {

  def getSAStatus(utr: SAUTR, taxYear: TaxYear)(implicit hc: HeaderCarrier): EitherT[Future, Error, SAStatusResponse]

  def getCTStatus(utr: CTUTR, from: LocalDate, to: LocalDate)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, CTStatusResponse]

}

@Singleton
class IFServiceImpl @Inject() (
  IFConnector: IFConnector
)(implicit ec: ExecutionContext)
    extends IFService {

  private def handleErrorPath(httpResponse: HttpResponse, utrType: String) = {
    val responseError = s"Response to get $utrType status came back with status ${httpResponse.status}"
    httpResponse.parseJSON[RawFailureResponse] match {
      case Left(_)         => Left(Error(s"$responseError; could not parse body"))
      case Right(failures) => Left(Error(s"$responseError - ${failures.failures}"))
    }
  }

  override def getSAStatus(
    utr: SAUTR,
    taxYear: TaxYear
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, SAStatusResponse] =
    IFConnector
      .getSAStatus(utr, taxYear)
      .subflatMap { httpResponse =>
        if (httpResponse.status === OK) {
          httpResponse
            .parseJSON[RawSASuccessResponse]
            .leftMap(Error(_))
            .flatMap(response =>
              Either
                .fromOption(
                  SAStatus.fromString(response.returnStatus),
                  Error(s"Could not parse success return status ${response.returnStatus}")
                )
                .map(SAStatusResponse(utr, taxYear, _))
            )
        } else {
          handleErrorPath(httpResponse, utrType = "SA")
        }
      }

  override def getCTStatus(
    utr: CTUTR,
    from: LocalDate,
    to: LocalDate
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, CTStatusResponse] =
    IFConnector
      .getCTStatus(utr, from, to)
      .subflatMap { httpResponse =>
        if (httpResponse.status === OK) {
          httpResponse
            .parseJSON[RawCTSuccessResponse]
            .leftMap(Error(_))
            .flatMap(response =>
              Either
                .fromOption(
                  CTStatus.fromString(response.returnStatus),
                  Error(s"Could not parse success return status ${response.returnStatus}")
                )
                .map(CTStatusResponse(utr, from, to, _, response.accountingPeriods))
            )
        } else {
          handleErrorPath(httpResponse, utrType = "CT")
        }
      }
}

object IFServiceImpl {
  final case class RawSASuccessResponse(
    returnStatus: String
  )
  implicit val rawSASuccessReads: Reads[RawSASuccessResponse] = Json.reads

  final case class RawCTSuccessResponse(
    returnStatus: String,
    accountingPeriods: List[AccountingPeriod]
  )
  implicit val rawCTSuccessReads: Reads[RawCTSuccessResponse] = Json.reads

  final case class RawFailureResponseFailure(
    code: String,
    reason: String
  )
  implicit val rawFailureResponseFailureReads: Reads[RawFailureResponseFailure] = Json.reads

  final case class RawFailureResponse(
    failures: List[RawFailureResponseFailure]
  )
  implicit val rawFailureResponseReads: Reads[RawFailureResponse] = Json.reads
}
