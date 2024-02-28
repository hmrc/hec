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

package uk.gov.hmrc.hec.connectors

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.ids.CRN
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DESConnectorImpl])
trait DESConnector {
  def getCtutr(crn: CRN)(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse]
}

@Singleton
class DESConnectorImpl @Inject() (
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext)
    extends DESConnector {

  private val baseUrl: String = servicesConfig.baseUrl("des")

  private def getCturtUrl(crn: CRN): String = s"$baseUrl/corporation-tax/identifiers/crn/${crn.value}"

  private val bearerToken = servicesConfig.getString("microservice.services.des.bearer-token")
  private val environment = servicesConfig.getString("microservice.services.des.environment")

  private val headers: Seq[(String, String)] = Seq(
    "Authorization" -> s"Bearer $bearerToken",
    "Environment"   -> environment
  )

  override def getCtutr(crn: CRN)(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .GET[HttpResponse](getCturtUrl(crn), Seq.empty, headers)(
          HttpReads[HttpResponse],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )

}
