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

package uk.gov.hmrc.hec.controllers

import cats.data.EitherT
import cats.instances.future._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.hec.models._
import uk.gov.hmrc.hec.models.ids.{CTUTR, SAUTR}
import uk.gov.hmrc.hec.services.IFService
import uk.gov.hmrc.hec.services.IFService.{BackendError, DataNotFoundError, IFError}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class IFControllerSpec extends ControllerSpec {

  val mockIFService = mock[IFService]

  override val overrideBindings =
    List[GuiceableModule](
      bind[IFService].toInstance(mockIFService)
    )

  val controller = instanceOf[IFController]

  def mockGetSAStatus(utr: SAUTR, taxYear: TaxYear)(result: Either[IFError, SAStatusResponse]) =
    (mockIFService
      .getSAStatus(_: SAUTR, _: TaxYear, _: UUID)(_: HeaderCarrier))
      .expects(utr, taxYear, *, *)
      .returning(EitherT.fromEither(result))

  def mockGetCTStatus(utr: CTUTR, startDate: LocalDate, endDate: LocalDate)(result: Either[IFError, CTStatusResponse]) =
    (mockIFService
      .getCTStatus(_: CTUTR, _: LocalDate, _: LocalDate, _: UUID)(_: HeaderCarrier))
      .expects(utr, startDate, endDate, *, *)
      .returning(EitherT.fromEither(result))

  "IFController" when {

    "handling requests to fetch SA status" must {

      val validSautr = "1000062440"

      "return a 400 (bad request)" when {

        def testBadRequest(utr: String, taxYear: String, errorStr: String) = {
          val request = FakeRequest()
          val result  = controller.getSAStatus(utr, taxYear)(request)
          status(result)          shouldBe BAD_REQUEST
          contentAsString(result) shouldBe errorStr
        }

        "SAUTR is invalid" in {
          testBadRequest("invalid-utr", "2020", "Invalid SAUTR")
        }

        "the tax year contains less than 4 digits invalid" in {
          testBadRequest(validSautr, "202", "Invalid tax year")
        }

        "the tax year contains more than 4 digits invalid" in {
          testBadRequest(validSautr, "20200", "Invalid tax year")
        }

        "the tax year contains invalid characters" in {
          testBadRequest(validSautr, "abc1", "Invalid tax year")
        }

        "SAUTR and tax year format are both invalid" in {
          testBadRequest("invalid-sautr", "202", "Invalid SAUTR; Invalid tax year")
        }
      }

      "return an 500 (internal server error)" when {

        "there is an error fetching the SA status" in {
          val taxYear = "2020"
          mockGetSAStatus(SAUTR(validSautr), TaxYear(2020))(Left(BackendError(Error(new Exception("some error")))))

          val request = FakeRequest()

          val result = controller.getSAStatus(validSautr, taxYear)(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return an 404 (not found)" when {

        "there is an error fetching the SA status" in {
          val taxYear = "2020"
          mockGetSAStatus(SAUTR(validSautr), TaxYear(2020))(Left(DataNotFoundError("some error")))

          val request = FakeRequest()

          val result = controller.getSAStatus(validSautr, taxYear)(request)
          status(result) shouldBe NOT_FOUND
        }

      }

      "return a 200 success with result" when {

        "inputs are all valid and service returns success response" in {
          val utr        = SAUTR(validSautr)
          val taxYearStr = "2020"
          val taxYear    = TaxYear(taxYearStr.toInt)
          val response   = SAStatusResponse(
            sautr = utr,
            taxYear = taxYear,
            status = SAStatus.ReturnFound
          )
          mockGetSAStatus(utr, taxYear)(Right(response))

          val request = FakeRequest()
          val result  = controller.getSAStatus(validSautr, taxYearStr)(request)
          status(result) shouldBe OK
        }
      }

    }

    "handling requests to fetch CT status" must {

      val validCtutr           = "1000062440"
      val startDateStr         = "2020-10-10"
      val endDateStr           = "2021-10-10"
      val (startDate, endDate) = (LocalDate.parse(startDateStr), LocalDate.parse(endDateStr))

      "return a 400 (bad request)" when {

        def testBadRequest(utr: String, startDate: String, endDate: String, errorStr: String) = {
          val request = FakeRequest()
          val result  = controller.getCTStatus(utr, startDate, endDate)(request)
          status(result)          shouldBe BAD_REQUEST
          contentAsString(result) shouldBe errorStr
        }

        "CTUTR is invalid" in {
          testBadRequest("invalid-utr", startDateStr, endDateStr, "Invalid CTUTR")
        }

        "from date format is invalid" in {
          testBadRequest(validCtutr, "invalid date", endDateStr, "Invalid startDate format")
        }

        "to date format is invalid" in {
          testBadRequest(validCtutr, startDateStr, "invalid date", "Invalid endDate format")
        }
      }

      "return an 500 (internal server error)" when {

        "there is an error fetching the CT status" in {
          mockGetCTStatus(CTUTR(validCtutr), startDate, endDate)(Left(BackendError(Error(new Exception("some error")))))
          val request = FakeRequest()

          val result = controller.getCTStatus(validCtutr, startDateStr, endDateStr)(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return an 404 (not found)" when {

        "CTUTR was not found" in {
          mockGetCTStatus(CTUTR(validCtutr), startDate, endDate)(Left(DataNotFoundError("some error")))
          val request = FakeRequest()

          val result = controller.getCTStatus(validCtutr, startDateStr, endDateStr)(request)
          status(result) shouldBe NOT_FOUND
        }

      }

      "return a 200 success with result" when {

        "inputs are all valid and service returns success response" in {
          val utr      = CTUTR(validCtutr)
          val response = CTStatusResponse(
            ctutr = utr,
            startDate = startDate,
            endDate = endDate,
            latestAccountingPeriod = None
          )
          mockGetCTStatus(utr, startDate, endDate)(Right(response))

          val request = FakeRequest()
          val result  = controller.getCTStatus(validCtutr, startDateStr, endDateStr)(request)
          status(result) shouldBe OK
        }
      }

    }
  }

}
