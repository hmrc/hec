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

package uk.gov.hmrc.hec.connectors

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.hec.models.ids.{CTUTR, SAUTR}
import uk.gov.hmrc.hec.models.{Error, TaxYear}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[IFConnectorImpl])
trait IFConnector {

  def getSAStatus(utr: SAUTR, taxYear: TaxYear, correlationId: UUID)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def getCTStatus(
    utr: CTUTR,
    startDate: LocalDate,
    endDate: LocalDate,
    correlationId: UUID
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse]

}

@Singleton
class IFConnectorImpl @Inject() (http: HttpClient, servicesConfig: ServicesConfig)(implicit
  ec: ExecutionContext
) extends IFConnector {

  private def isoDateFormat(date: LocalDate) = date.format(DateTimeFormatter.ISO_DATE)

  private val baseUrl: String = servicesConfig.baseUrl("integration-framework")

  // Integration framework adopts the old convention of using the end date for the SA tax year, therefore adding 1
  private def saStatusUrl(utr: SAUTR, taxYear: TaxYear): String =
    s"$baseUrl/individuals/self-assessment/account-overview/${utr.value}/${taxYear.startYear + 1}"

  private def ctStatusUrl(utr: CTUTR, startDate: LocalDate, endDate: LocalDate): String =
    s"$baseUrl/organisations/corporation-tax/${utr.value}/company/accounting-periods?startDate=${isoDateFormat(startDate)}&endDate=${isoDateFormat(endDate)}"

  private val bearerToken = servicesConfig.getString("microservice.services.integration-framework.bearer-token")
  private val environment = servicesConfig.getString("microservice.services.integration-framework.environment")

  private def headers(correlationId: UUID): Seq[(String, String)] = Seq(
    "Authorization" -> s"Bearer $bearerToken",
    "Environment"   -> environment,
    "CorrelationId" -> correlationId.toString
  )

  override def getSAStatus(
    utr: SAUTR,
    taxYear: TaxYear,
    correlationId: UUID
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .GET[HttpResponse](saStatusUrl(utr, taxYear), Seq.empty, headers(correlationId))(
          HttpReads[HttpResponse],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )

  override def getCTStatus(
    utr: CTUTR,
    startDate: LocalDate,
    endDate: LocalDate,
    correlationId: UUID
  )(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .GET[HttpResponse](ctStatusUrl(utr, startDate, endDate), Seq.empty, headers(correlationId))(
          HttpReads[HttpResponse],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )

}
