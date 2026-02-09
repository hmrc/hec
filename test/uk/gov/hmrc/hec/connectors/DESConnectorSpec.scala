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

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.*
import org.scalatest.wordspec.*
import play.api.Configuration
import uk.gov.hmrc.hec.models.ids.CRN
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class DESConnectorSpec extends AnyWordSpec with Matchers with MockFactory with HttpSupport with ConnectorSpec {

  val (protocol, host, port, bearerToken, environment) = ("http", "host", "123", "token", "environment")

  val config = Configuration(
    ConfigFactory.parseString(s"""
                                 | microservice.services.des {
                                 |    host     = $host
                                 |    port     = $port
                                 |    bearer-token = $bearerToken
                                 |    environment = $environment
                                 |  }
                                 |""".stripMargin)
  )

  val connector = new DESConnectorImpl(mockHttp, new ServicesConfig(config))

  "DESConnectorImpl" when {

    val crn     = "1234567890"
    val headers = Seq(
      "Authorization" -> s"Bearer $bearerToken",
      "Environment"   -> environment
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    "handling requests to fetch CTUTR" must {
      val expectedUrl = url"$protocol://$host:$port/corporation-tax/identifiers/crn/$crn"

      behave like connectorBehaviour(
        mockGet(expectedUrl, headers)(_),
        () => connector.getCtutr(CRN(crn))
      )
    }

  }

}
