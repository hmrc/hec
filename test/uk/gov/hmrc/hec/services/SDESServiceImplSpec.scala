/*
 * Copyright 2023 HM Revenue & Customs
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
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers._
import uk.gov.hmrc.hec.connectors.SDESConnector
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.sdes.{FileAudit, FileChecksum, FileMetaData, SDESFileNotifyRequest}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SDESServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {
  val mockSDESConnector = mock[SDESConnector]

  def mockNotifyFile(notifyFileRequest: SDESFileNotifyRequest)(result: Either[Error, HttpResponse]) =
    (mockSDESConnector
      .notify(_: SDESFileNotifyRequest)(_: HeaderCarrier))
      .expects(notifyFileRequest, *)
      .returning(EitherT.fromEither[Future](result))

  val service = new SDESServiceImpl(mockSDESConnector)
  val uuid    = UUID.randomUUID()

  val fileNotifyRequest = SDESFileNotifyRequest(
    "ssa",
    FileMetaData(
      "hec",
      "file1.dat",
      s"http://localhost:8464/object-store/object/hec/tax-checks/file1.dat",
      FileChecksum(value = "hashValue"),
      2000,
      List()
    ),
    FileAudit(uuid.toString)
  )

  "SDESServiceImplSPec" when {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    def testIsError(response: Either[Error, HttpResponse]): Unit = {
      mockNotifyFile(fileNotifyRequest)(response)
      val result = await(service.fileNotify(fileNotifyRequest).value)
      result shouldBe a[Left[_, _]]
    }

    "return an error" when {
      "the call to notify sdes about created file fails" in {
        testIsError(Left(Error("")))
      }

      "the call to notify sdes about created file returns with a 500 response" in {
        testIsError(Right(HttpResponse(500, "")))
      }

      "the call to notify sdes about created file returns with a 400 response" in {
        testIsError(Right(HttpResponse(400, "")))
      }

    }

    "return successfully" when {

      " the response is no content" in {
        mockNotifyFile(fileNotifyRequest)(Right(HttpResponse(204, "")))
        val result = await(service.fileNotify(fileNotifyRequest).value)
        result shouldBe (Right(()))
      }

    }
  }

}
