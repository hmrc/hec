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

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.hec.models.sdes.{FileAudit, FileChecksum, FileMetaData, SDESFileNotifyRequest}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class SDESConnectorSpec extends AnyWordSpec with Matchers with MockFactory with HttpSupport with ConnectorSpec {
  val (protocol, host, port)                = ("http", "host", "123")
  val (serverTokenHeader, serverTokenValue) = "header" -> "token"
  val config                                = Configuration(
    ConfigFactory.parseString(s"""
                                 | microservice.services.sdes {
                                 |    host     = $host
                                 |    port     = $port
                                 |  }
                                 |  hec-file-extraction-details {
                                 |   file-notification-api {
                                 |       location = "sdes-stub"
                                 |       server-token-header = "$serverTokenHeader"
                                 |       server-token-value = "$serverTokenValue"
                                 |      }
                                 |   }
                                 |
                                 |""".stripMargin)
  )

  val connector     = new SDESConnectorImpl(mockHttp, new ServicesConfig(config), config)
  val uuid          = UUID.randomUUID()
  val notifyRequest = SDESFileNotifyRequest(
    "ssa",
    FileMetaData(
      "hec",
      "file1.dat",
      "http://localhost:8464/object-store/object/hec/tax-checks/file1.dat",
      FileChecksum(value = "hashValue"),
      2000,
      List()
    ),
    FileAudit(uuid.toString)
  )

  "SDESConnectorImpl" when {
    val expectedUrl = s"$protocol://$host:$port/sdes-stub/notification/fileready"

    "handling requests to notify  SDES about the files generated" must {
      implicit val hc: HeaderCarrier = HeaderCarrier().copy(authorization = Some(Authorization("bearer")))

      behave like connectorBehaviour(
        mockPost(expectedUrl, Seq(serverTokenHeader -> serverTokenValue), notifyRequest)(_),
        () => connector.notify(notifyRequest)
      )
    }
  }
}
