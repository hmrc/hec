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
import uk.gov.hmrc.hec.connectors.IFConnector
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxYear
import uk.gov.hmrc.hec.models.hecTaxCheck.company.CTAccountingPeriod.CTAccountingPeriodDigital
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTStatus, CTStatusResponse}
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.{SAStatus, SAStatusResponse}
import uk.gov.hmrc.hec.models.ids.{CTUTR, SAUTR}
import uk.gov.hmrc.hec.services.IFService.{BackendError, DataNotFoundError}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class IFServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockIFConnector = mock[IFConnector]

  def mockGetSAStatus(utr: SAUTR, taxYear: TaxYear)(response: Either[Error, HttpResponse]) =
    (mockIFConnector
      .getSAStatus(_: SAUTR, _: TaxYear, _: UUID)(_: HeaderCarrier))
      .expects(utr, taxYear, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockGetCTStatus(utr: CTUTR, fromDate: LocalDate, toDate: LocalDate)(response: Either[Error, HttpResponse]) =
    (mockIFConnector
      .getCTStatus(_: CTUTR, _: LocalDate, _: LocalDate, _: UUID)(_: HeaderCarrier))
      .expects(utr, fromDate, toDate, *, *)
      .returning(EitherT.fromEither[Future](response))

  val service = new IFServiceImpl(mockIFConnector)

  "IFServiceImpl" when {

    def getErrorJson(code: String = "some code") =
      s"""{
         | "failures" : [
         |   {
         |     "code": "$code",
         |     "reason": "some reason"
         |   }
         |  ]
         | } """.stripMargin

    val errorJson = getErrorJson()

    "handling requests to fetch SA status" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      val utr           = SAUTR("some-sa-utr")
      val taxYear       = TaxYear(2020)
      val correlationId = UUID.randomUUID()

      "return an error" when {

        def testIsError[A](response: Either[Error, HttpResponse])(implicit c: ClassTag[A]): Unit = {
          mockGetSAStatus(utr, taxYear)(response)
          val result = await(service.getSAStatus(utr, taxYear, correlationId).value)
          result                     shouldBe a[Left[_, _]]
          result.left.toOption.value shouldBe a[A]
        }

        "the call to fetch SA status fails" in {
          testIsError[BackendError](Left(Error("")))
        }

        "the call to fetch SA status returns with a 500 response" in {
          testIsError[BackendError](Right(HttpResponse(500, "")))
        }

        "the call to fetch SA status returns with a 400 response" in {
          testIsError[BackendError](Right(HttpResponse(400, "")))
        }

        "the call to fetch SA status returns with 404 not found response" in {
          testIsError[BackendError](Right(HttpResponse(404, errorJson)))
        }

        "the call to fetch SA status returns with 404 not found response with NO_DATA_FOUND code" in {
          val notFoundErrorJson = getErrorJson("NO_DATA_FOUND")
          testIsError[DataNotFoundError](Right(HttpResponse(404, notFoundErrorJson)))
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

        "there is a value for 'returnStatus' which cannot be parsed" in {
          val json = Json.parse(s"""{ "returnStatus": "abc" }""")
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
          val testCases = List(
            "1" -> SAStatus.ReturnFound,
            "2" -> SAStatus.NoticeToFileIssued,
            "3" -> SAStatus.NoReturnFound
          )

          testCases.foreach { case (responseString, expectedSaStatus) =>
            withClue(s"For response string $responseString and expected SA status $expectedSaStatus: ") {}
            val connectorResponse = s"""{ "returnStatus": "$responseString" }"""
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
              status = expectedSaStatus
            )

            val result = service.getSAStatus(utr, taxYear, correlationId).value
            await(result) shouldBe Right(expectedSAStatusResponse)
          }

        }

      }

    }

    "handling requests to fetch CT status" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      val utr           = CTUTR("some-ct-utr")
      val fromDate      = LocalDate.of(2020, 10, 1)
      val toDate        = LocalDate.of(2021, 10, 1)
      val correlationId = UUID.randomUUID()

      "return an error" when {

        val errorJson =
          s"""{
             | "failures" : [
             |   {
             |     "code": "some code",
             |     "reason": "some reason"
             |   }
             |  ]
             | } """.stripMargin

        def testIsError[A](response: Either[Error, HttpResponse])(implicit c: ClassTag[A]): Unit = {
          mockGetCTStatus(utr, fromDate, toDate)(response)
          val result = await(service.getCTStatus(utr, fromDate, toDate, correlationId).value)
          result                     shouldBe a[Left[_, _]]
          result.left.toOption.value shouldBe a[A]
        }

        "the call fails" in {
          testIsError[BackendError](Left(Error("")))
        }

        "the call returns with a 400 response" in {
          testIsError[BackendError](Right(HttpResponse(400, errorJson)))
        }

        "the call returns with a 500 response" in {
          testIsError[BackendError](Right(HttpResponse(500, errorJson)))
        }

        "the call to fetch status returns with 404 not found response" in {
          testIsError[BackendError](Right(HttpResponse(404, errorJson)))
        }

        "the call to fetch status returns with 404 not found response with NO_DATA_FOUND code" in {
          val notFoundErrorJson = getErrorJson("NO_DATA_FOUND")
          testIsError[DataNotFoundError](Right(HttpResponse(404, notFoundErrorJson)))
        }

        "there is no JSON in the response body" in {
          testIsError[BackendError](Right(HttpResponse(200, "")))
        }

        "there is invalid JSON in the response body" in {
          val responseHeaders = Map.empty[String, Seq[String]]

          testIsError[BackendError](
            Right(
              HttpResponse(
                200,
                Json.parse("""{ "a" : "b" }"""),
                responseHeaders
              )
            )
          )
        }

        "the value for the 'returnStatus' field is not understood" in {
          val connectorResponse =
            s"""{ "returnStatus": "1" }""".stripMargin

          testIsError[BackendError](
            Right(
              HttpResponse(
                200,
                Json.parse(connectorResponse),
                Map.empty[String, Seq[String]]
              )
            )
          )
        }

        "there is a value for the 'accountingPeriodStatus' field which is not understood" in {
          val connectorResponse =
            s"""{
               | "returnStatus": "0",
               | "accountingPeriods": [
               |   {
               |     "accountingPeriodStartDate": "2020-10-15",
               |     "accountingPeriodEndDate": "2021-10-20",
               |     "accountingPeriodStatus": "0"
               |   }
               | ]
               | }""".stripMargin

          testIsError[BackendError](
            Right(
              HttpResponse(
                200,
                Json.parse(connectorResponse),
                Map.empty[String, Seq[String]]
              )
            )
          )
        }

        "the 'returnStatus' field indicates that live records were found but no accounting periods can be found" in {
          val connectorResponse =
            s"""{
               | "returnStatus": "0"
               | }""".stripMargin

          testIsError[BackendError](
            Right(
              HttpResponse(
                200,
                Json.parse(connectorResponse),
                Map.empty[String, Seq[String]]
              )
            )
          )
        }

      }

      "return successfully" when {

        "the 'returnStatus' field indicates no live records were found" in {
          val connectorResponse =
            s"""{ "returnStatus": "2" }""".stripMargin

          mockGetCTStatus(utr, fromDate, toDate)(
            Right(
              HttpResponse(
                200,
                Json.parse(connectorResponse),
                Map.empty[String, Seq[String]]
              )
            )
          )

          val expectedCTStatusResponse = CTStatusResponse(utr, fromDate, toDate, None)
          val result                   = service.getCTStatus(utr, fromDate, toDate, correlationId).value

          await(result) shouldBe Right(expectedCTStatusResponse)
        }

        "the 'returnStatus' field indicates live records were found successfully" in {
          val testCases = List(
            "1" -> CTStatus.ReturnFound,
            "2" -> CTStatus.NoticeToFileIssued,
            "3" -> CTStatus.NoReturnFound
          )

          testCases.foreach { case (responseString, expectedCtStatus) =>
            withClue(s"For response string $responseString and expected CT status $expectedCtStatus: ") {}
            val connectorResponse =
              s"""{
                   | "returnStatus": "0",
                   | "accountingPeriods": [
                   |   {
                   |     "accountingPeriodStartDate": "2020-10-15",
                   |     "accountingPeriodEndDate": "2021-10-20",
                   |     "accountingPeriodStatus": "$responseString"
                   |   },
                   |   {
                   |     "accountingPeriodStartDate": "2020-10-16",
                   |     "accountingPeriodEndDate": "2021-10-19",
                   |     "accountingPeriodStatus": "$responseString"
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
              fromDate,
              toDate,
              Some(
                CTAccountingPeriodDigital(
                  LocalDate.of(2020, 10, 15),
                  LocalDate.of(2021, 10, 20),
                  expectedCtStatus
                )
              )
            )

            val result = service.getCTStatus(utr, fromDate, toDate, correlationId).value
            await(result) shouldBe Right(expectedCTStatusResponse)
          }
        }

      }

    }

  }

}
