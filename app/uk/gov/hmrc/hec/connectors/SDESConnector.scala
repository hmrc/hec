/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.Configuration
import play.api.libs.json.Writes
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.sdes.SDESFileNotifyRequest
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SDESConnectorImpl])
trait SDESConnector {
  def notify(fileNotifyRequest: SDESFileNotifyRequest)(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse]
}

@Singleton
class SDESConnectorImpl @Inject() (http: HttpClient, servicesConfig: ServicesConfig, config: Configuration)(implicit
  ec: ExecutionContext
) extends SDESConnector {

  private val baseUrl: String = servicesConfig.baseUrl("sdes")

  private def getApiConfigString(configParameter: String): String =
    config.get[String](s"hec-file-extraction-details.file-notification-api.$configParameter")

  private val (serverTokenHeader, serverTokenValue) =
    getApiConfigString("server-token-header") -> getApiConfigString("server-token-value")

  private val extraHeaders = Seq(serverTokenHeader -> serverTokenValue)

  private val apiLocation: String = getApiConfigString("location")

  private val sdesUrl: String =
    if (apiLocation.isEmpty) s"$baseUrl/notification/fileready" else s"$baseUrl/$apiLocation/notification/fileready"

  override def notify(
    fileNotifyRequest: SDESFileNotifyRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .POST[SDESFileNotifyRequest, HttpResponse](sdesUrl, fileNotifyRequest, extraHeaders)(
          implicitly[Writes[SDESFileNotifyRequest]],
          implicitly[HttpReads[HttpResponse]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
}
