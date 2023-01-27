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

package uk.gov.hmrc.hec.controllers

import cats.data.EitherT
import cats.instances.future._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.hec.models._
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR, GGCredId}
import uk.gov.hmrc.hec.services.DESService
import uk.gov.hmrc.hec.services.DESService.{BackendError, DESError, DataNotFoundError, InvalidCRNError}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class DESControllerSpec extends ControllerSpec with AuthSupport {

  private val mockDESService = mock[DESService]

  override val overrideBindings =
    List[GuiceableModule](
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[DESService].toInstance(mockDESService)
    )
  val ggCredId                  = GGCredId("ggCredId")

  private val controller = instanceOf[DESController]

  private def mockGetCtutr(crn: CRN)(result: Either[DESError, CTUTR]) =
    (mockDESService
      .getCtutr(_: CRN)(_: HeaderCarrier))
      .expects(crn, *)
      .returning(EitherT.fromEither(result))

  "DESController" when {

    "handling requests to fetch CTUTR" must {

      val validCrn = "AB123456"

      val request = FakeRequest()

      "return a 400 (bad request)" when {

        "input CRN is invalid" in {
          mockGGAuthWithGGRetrieval(ggCredId.value)
          val result = controller.getCtutr("invalid-crn")(request)
          status(result)          shouldBe BAD_REQUEST
          contentAsString(result) shouldBe "Invalid CRN"
        }

        "DES API says CRN is invalid" in {
          mockGGAuthWithGGRetrieval(ggCredId.value)
          mockGetCtutr(CRN(validCrn))(Left(InvalidCRNError("some error")))

          val result = controller.getCtutr(validCrn)(request)
          status(result)          shouldBe BAD_REQUEST
          contentAsString(result) shouldBe "Invalid CRN from DES"
        }
      }

      "return an 500 (internal server error)" when {

        "there is an error fetching the CTUTR" in {
          mockGGAuthWithGGRetrieval(ggCredId.value)
          mockGetCtutr(CRN(validCrn))(Left(BackendError(Error(new Exception("some error")))))

          val request = FakeRequest()

          val result = controller.getCtutr(validCrn)(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return an 404 (not found)" when {

        "CTUTR is not found" in {
          mockGGAuthWithGGRetrieval(ggCredId.value)
          mockGetCtutr(CRN(validCrn))(Left(DataNotFoundError("some error")))

          val request = FakeRequest()

          val result = controller.getCtutr(validCrn)(request)
          status(result) shouldBe NOT_FOUND
        }

      }

      "return a 200 success with result" when {

        "inputs are all valid and service returns success response" in {
          val crn = CRN(validCrn)
          mockGGAuthWithGGRetrieval(ggCredId.value)
          mockGetCtutr(crn)(Right(CTUTR("some-utr")))

          val request = FakeRequest()
          val result  = controller.getCtutr(validCrn)(request)
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.obj("ctutr" -> "some-utr")
        }
      }

      "return 403(forbidden) if not authenticated" in {
        mockGGAuthWithForbidden()
        val request = FakeRequest()
        val result  = controller.getCtutr(validCrn)(request)
        status(result) shouldBe FORBIDDEN

      }

    }
  }

}
