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

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should._
import org.scalatest.wordspec._
import play.api.Configuration
import uk.gov.hmrc.hec.models.TaxYear
import uk.gov.hmrc.hec.models.ids.{CTUTR, SAUTR}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class IFConnectorSpec extends AnyWordSpec with Matchers with MockFactory with HttpSupport with ConnectorSpec {

  val (protocol, host, port, bearerToken, environment) = ("http", "host", "123", "token", "environment")

  val config = Configuration(
    ConfigFactory.parseString(s"""
                                 | microservice.services.integration-framework {
                                 |    host     = $host
                                 |    port     = $port
                                 |    bearer-token = $bearerToken
                                 |    environment = $environment
                                 |  }
                                 |""".stripMargin)
  )

  val connector = new IFConnectorImpl(mockHttp, new ServicesConfig(config))

  "IFConnectorImpl" when {

    val utr           = "1234567890"
    val correlationId = "correlationId"
    val headers       = Seq(
      "Authorization" -> s"Bearer $bearerToken",
      "Environment"   -> environment,
      "CorrelationId" -> correlationId
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    "handling requests to fetch SA status" must {
      val taxYear = 2020

      val expectedUrl = s"$protocol://$host:$port/individuals/self-assessment/account-overview/$utr/${taxYear + 1}"

      behave like connectorBehaviour(
        mockGet[HttpResponse](expectedUrl, headers)(_),
        () => connector.getSAStatus(SAUTR(utr), TaxYear(taxYear), correlationId)
      )
    }

    "handling requests to fetch CT status" must {
      val fromDate = "2020-10-01"
      val toDate   = "2021-10-01"

      val expectedUrl =
        s"$protocol://$host:$port/organisations/corporation-tax/$utr/company/accounting-periods?startDate=$fromDate&endDate=$toDate"

      behave like connectorBehaviour(
        mockGet[HttpResponse](expectedUrl, headers)(_),
        () => connector.getCTStatus(CTUTR(utr), LocalDate.of(2020, 10, 1), LocalDate.of(2021, 10, 1), correlationId)
      )
    }

  }

}
