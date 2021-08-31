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
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.hec.connectors.IFConnector
import uk.gov.hmrc.hec.models.ids.{CTUTR, SAUTR}
import uk.gov.hmrc.hec.models.{AccountingPeriod, CTStatus, CTStatusResponse, Error, SAStatus, SAStatusResponse, TaxYear}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IFServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockIFConnector = mock[IFConnector]

  def mockGetSAStatus(utr: SAUTR, taxYear: TaxYear)(response: Either[Error, HttpResponse]) =
    (mockIFConnector
      .getSAStatus(_: SAUTR, _: TaxYear)(_: HeaderCarrier))
      .expects(utr, taxYear, *)
      .returning(EitherT.fromEither[Future](response))

  def mockGetCTStatus(utr: CTUTR, fromDate: LocalDate, toDate: LocalDate)(response: Either[Error, HttpResponse]) =
    (mockIFConnector
      .getCTStatus(_: CTUTR, _: LocalDate, _: LocalDate)(_: HeaderCarrier))
      .expects(utr, fromDate, toDate, *)
      .returning(EitherT.fromEither[Future](response))

  val service = new IFServiceImpl(mockIFConnector)

  "IFServiceImpl" when {

    "handling requests to fetch SA status" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      val utr     = SAUTR("some-sa-utr")
      val taxYear = TaxYear(2020)

      "return an error" when {

        def testIsError(response: Either[Error, HttpResponse]): Unit = {
          mockGetSAStatus(utr, taxYear)(response)
          await(service.getSAStatus(utr, taxYear).value) shouldBe a[Left[_, _]]
        }

        "the call to fetch SA status fails" in {
          testIsError(Left(Error("")))
        }

        "the call to fetch SA status returns with a non-OK response" in {
          testIsError(Right(HttpResponse(500, "")))
        }

        "there is no JSON in the response body" in {
          testIsError(Right(HttpResponse(200, "")))
        }

        "the JSON cannot be parsed in the response body" in {
          val json = Json.parse("""{ "a" : "b" }""")
          testIsError(
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

          val connectorResponse = """{ "returnStatus": "Return Found" }"""
          mockGetSAStatus(utr, taxYear)(
            Right(
              HttpResponse(
                200,
                Json.parse(connectorResponse),
                Map.empty[String, Seq[String]]
              )
            )
          )

          val expectedSAStatusResponse = SAStatusResponse(
            sautr = utr,
            taxYear = taxYear,
            status = SAStatus.ReturnFound
          )

          val result = service.getSAStatus(utr, taxYear).value
          await(result) shouldBe Right(expectedSAStatusResponse)
        }

      }

    }

    "handling requests to fetch CT status" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      val utr      = CTUTR("some-ct-utr")
      val fromDate = LocalDate.of(2020, 10, 1)
      val toDate   = LocalDate.of(2021, 10, 1)

      "return an error" when {

        def testIsError(response: Either[Error, HttpResponse]): Unit = {
          mockGetCTStatus(utr, fromDate, toDate)(response)
          await(service.getCTStatus(utr, fromDate, toDate).value) shouldBe a[Left[_, _]]
        }

        "the call fails" in {
          testIsError(Left(Error("")))
        }

        "the call returns with a non-OK response" in {
          testIsError(Right(HttpResponse(500, "")))
        }

        "there is no JSON in the response body" in {
          testIsError(Right(HttpResponse(200, "")))
        }

        "the JSON cannot be parsed in the response body" in {
          val responseHeaders = Map.empty[String, Seq[String]]

          testIsError(
            Right(
              HttpResponse(
                200,
                Json.parse("""{ "a" : "b" }"""),
                responseHeaders
              )
            )
          )
        }
      }

      "return successfully" when {

        "the response is OK and the json body can be parsed" in {

          val connectorResponse =
            """{
              | "returnStatus": "No Return Found",
              | "accountingPeriods": [
              |   {
              |     "accountingPeriod": "01",
              |     "accountingPeriodStartDate": "2020-10-01",
              |     "accountingPeriodEndDate": "2021-10-01"
              |   }
              | ]
              | }""".stripMargin

          mockGetCTStatus(utr, fromDate, toDate)(
            Right(
              HttpResponse(
                200,
                Json.parse(connectorResponse),
                Map.empty[String, Seq[String]]
              )
            )
          )

          val expectedCTStatusResponse = CTStatusResponse(
            ctutr = utr,
            fromDate = LocalDate.parse("2020-10-01"),
            toDate = LocalDate.parse("2021-10-01"),
            status = CTStatus.NoReturnFound,
            accountingPeriods = List(AccountingPeriod("01", fromDate, toDate))
          )

          val result = service.getCTStatus(utr, fromDate, toDate).value
          await(result) shouldBe Right(expectedCTStatusResponse)
        }

      }

    }

  }

}
