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

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.hec.connectors.DESConnector
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR}
import uk.gov.hmrc.hec.services.DESService.{BackendError, DataNotFoundError, InvalidCRNError}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class DESServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockDESConnector = mock[DESConnector]

  def mockGetCtutr(crn: CRN)(response: Either[Error, HttpResponse]) =
    (mockDESConnector
      .getCtutr(_: CRN)(_: HeaderCarrier))
      .expects(crn, *)
      .returning(EitherT.fromEither[Future](response))

  val service = new DESServiceImpl(mockDESConnector)

  "DESServiceImpl" when {

    def getErrorJson(code: String = "some code") =
      s"""{
         |  "code": "$code",
         |  "reason": "some reason"
         |} """.stripMargin

    val errorJson = getErrorJson()

    "handling requests to fetch CTUTR" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      val crn = CRN("crn")

      "return an error" when {

        def testIsError[A](response: Either[Error, HttpResponse])(implicit c: ClassTag[A]): Unit = {
          mockGetCtutr(crn)(response)
          val result = await(service.getCtutr(crn).value)
          result                     shouldBe a[Left[_, _]]
          result.left.toOption.value shouldBe a[A]
        }

        "the call to fetch CTUTR fails" in {
          testIsError[BackendError](Left(Error("")))
        }

        "the call to fetch CTUTR returns with a 500 response" in {
          testIsError[BackendError](Right(HttpResponse(500, "")))
        }

        "the call to fetch CTUTR returns with a 503 response" in {
          testIsError[BackendError](Right(HttpResponse(503, "")))
        }

        "the call to fetch CTUTR returns with a 400 response" in {
          testIsError[BackendError](Right(HttpResponse(400, "")))
        }

        "the call to fetch CTUTR returns with 404 not found response" in {
          testIsError[BackendError](Right(HttpResponse(404, errorJson)))
        }

        "the call to fetch CTUTR returns with 404 not found response with NO_DATA_FOUND code" in {
          val notFoundErrorJson = getErrorJson("NO_DATA_FOUND")
          testIsError[DataNotFoundError](Right(HttpResponse(404, notFoundErrorJson)))
        }

        "the call to fetch CTUTR returns with 400 bad request response" in {
          testIsError[BackendError](Right(HttpResponse(400, errorJson)))
        }

        "the call to fetch CTUTR returns with 400 bad request response with INVALID_CRN code" in {
          val invalidCrnErrorJson = getErrorJson("INVALID_CRN")
          testIsError[InvalidCRNError](Right(HttpResponse(400, invalidCrnErrorJson)))
        }

        "there is no JSON in the response body" in {
          testIsError[BackendError](Right(HttpResponse(200, "")))
        }

        "there is invalid JSON in the response body" in {
          val json = Json.parse("""{ "a" : "b" }""")
          testIsError[BackendError](
            Right(
              HttpResponse(
                200,
                json,
                Map.empty[String, Seq[String]]
              )
            )
          )
        }

        "a successful response is received but the CT UTR in the response is not valid" in {
          val json = Json.parse("""{ "CTUTR": "1234567890" }""")
          testIsError[BackendError](
            Right(
              HttpResponse(
                200,
                json,
                Map.empty[String, Seq[String]]
              )
            )
          )
        }

      }

      "return successfully" when {

        "the response is OK and the json body can be parsed" in {
          val ctutr             = "2234567890"
          val connectorResponse = s"""{ "CTUTR": "$ctutr" }"""
          mockGetCtutr(crn)(
            Right(
              HttpResponse(
                200,
                Json.parse(connectorResponse),
                Map.empty[String, Seq[String]]
              )
            )
          )

          val result = service.getCtutr(crn).value
          await(result) shouldBe Right(CTUTR(ctutr))
        }

      }

    }

  }

}
