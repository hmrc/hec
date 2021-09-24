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
import play.api.libs.json._
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.hec.models.ApplicantDetails.IndividualApplicantDetails
import uk.gov.hmrc.hec.models.HECTaxCheckData.IndividualHECTaxCheckData
import uk.gov.hmrc.hec.models.TaxDetails.IndividualTaxDetails
import uk.gov.hmrc.hec.models.ids.{CRN, GGCredId, NINO, SAUTR}
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.{DateOfBirth, Error, HECTaxCheck, HECTaxCheckCode, HECTaxCheckData, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckMatchStatus, Name, TaxCheckListItem, TaxSituation}
import uk.gov.hmrc.hec.services.TaxCheckService
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxCheckControllerSpec extends ControllerSpec with AuthSupport {

  val mockTaxCheckService = mock[TaxCheckService]

  override val overrideBindings =
    List[GuiceableModule](
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[TaxCheckService].toInstance(mockTaxCheckService)
    )

  val controller = instanceOf[TaxCheckController]

  def mockSaveTaxCheck(taxCheckData: HECTaxCheckData)(result: Either[Error, HECTaxCheck]) =
    (mockTaxCheckService
      .saveTaxCheck(_: HECTaxCheckData)(_: HeaderCarrier))
      .expects(taxCheckData, *)
      .returning(EitherT.fromEither(result))

  def mockMatchTaxCheck(matchRequest: HECTaxCheckMatchRequest)(result: Either[Error, HECTaxCheckMatchResult]) =
    (mockTaxCheckService
      .matchTaxCheck(_: HECTaxCheckMatchRequest)(_: HeaderCarrier))
      .expects(matchRequest, *)
      .returning(EitherT.fromEither(result))

  def mockGetValidTaxCheckCodes(ggCredId: GGCredId)(result: Either[Error, List[TaxCheckListItem]]) =
    (mockTaxCheckService
      .getUnexpiredTaxCheckCodes(_: GGCredId)(_: HeaderCarrier))
      .expects(ggCredId, *)
      .returning(EitherT.fromEither(result))

  "TaxCheckController" when {

    "handling requests to save a tax check" must {

      def performActionWithJsonBody(requestBody: JsValue): Future[Result] = {
        val request = FakeRequest().withBody(requestBody).withHeaders(CONTENT_TYPE -> JSON)
        controller.saveTaxCheck(request)
      }

      val taxCheckData: HECTaxCheckData = IndividualHECTaxCheckData(
        IndividualApplicantDetails(GGCredId(""), Name("", ""), DateOfBirth(LocalDate.now())),
        LicenceDetails(
          LicenceType.ScrapMetalDealerSite,
          LicenceTimeTrading.EightYearsOrMore,
          LicenceValidityPeriod.UpToOneYear
        ),
        IndividualTaxDetails(
          NINO(""),
          Some(SAUTR("")),
          TaxSituation.PAYE,
          None
        )
      )

      "return a 415 (unsupported media type)" when {

        "there is no body in the request" in {
          val result: Future[Result] = controller.saveTaxCheck(FakeRequest()).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

        }

        "there is no json body in the request" in {
          val result: Future[Result] =
            controller.saveTaxCheck(FakeRequest().withBody("hi").withHeaders(CONTENT_TYPE -> TEXT)).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

        }

      }

      "return a 400 (bad request)" when {

        "the JSON in the request cannot be parsed" in {
          status(performActionWithJsonBody(JsString("hi"))) shouldBe BAD_REQUEST

        }

      }

      "return an 500 (internal server error)" when {

        "there is an error saving the tax check" in {
          mockSaveTaxCheck(taxCheckData)(Left(Error(new Exception("Oh no!"))))

          val result = performActionWithJsonBody(Json.toJson(taxCheckData))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a created (201)" when {

        "the tax check has been saved" in {
          val taxCheckCode     = HECTaxCheckCode("code")
          val expiresAfterDate = LocalDate.MIN
          val taxCheck         = HECTaxCheck(taxCheckData, taxCheckCode, expiresAfterDate, TimeUtils.now())

          mockSaveTaxCheck(taxCheckData)(Right(taxCheck))

          val result = performActionWithJsonBody(Json.toJson(taxCheckData))
          status(result)                              shouldBe CREATED
          contentAsJson(result).validate[HECTaxCheck] shouldBe JsSuccess(taxCheck)
        }

      }

    }

    "handling requests to match a tax check" must {

      def performActionWithJsonBody(requestBody: JsValue): Future[Result] = {
        val request = FakeRequest().withBody(requestBody).withHeaders(CONTENT_TYPE -> JSON)
        controller.matchTaxCheck(request)
      }

      val taxCheckCode = HECTaxCheckCode("code")
      val licenceType  = LicenceType.ScrapMetalDealerSite
      val dateOfBirth  = DateOfBirth(LocalDate.now())
      val crn          = CRN("crn")

      val individualMatchRequest: HECTaxCheckMatchRequest =
        HECTaxCheckMatchRequest(taxCheckCode, licenceType, Right(dateOfBirth))

      val companyMatchRequest: HECTaxCheckMatchRequest =
        HECTaxCheckMatchRequest(taxCheckCode, licenceType, Left(crn))

      "return a 415 (unsupported media type)" when {

        "there is no body in the request" in {
          val result: Future[Result] = controller.matchTaxCheck(FakeRequest()).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

        }

        "there is no json body in the request" in {
          val result: Future[Result] =
            controller.matchTaxCheck(FakeRequest().withBody("hi").withHeaders(CONTENT_TYPE -> TEXT)).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

        }

      }

      "return a 400 (bad request)" when {

        "the JSON in the request cannot be parsed" in {
          status(performActionWithJsonBody(JsString("hi"))) shouldBe BAD_REQUEST

        }

      }

      "return an 500 (internal server error)" when {

        "there is an error saving the tax check" in {
          mockMatchTaxCheck(individualMatchRequest)(Left(Error(new Exception("Oh no!"))))

          val result = performActionWithJsonBody(Json.toJson(individualMatchRequest))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 200 (OK)" when {

        "the tax check service returns a match result" in {

          val dateTime = TimeUtils.now()

          List[HECTaxCheckMatchResult](
            HECTaxCheckMatchResult(companyMatchRequest, dateTime, HECTaxCheckMatchStatus.Match),
            HECTaxCheckMatchResult(companyMatchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch),
            HECTaxCheckMatchResult(companyMatchRequest, dateTime, HECTaxCheckMatchStatus.Expired)
          ).foreach { matchResult =>
            withClue(s"For match result '$matchResult': ") {
              mockMatchTaxCheck(companyMatchRequest)(Right(matchResult))

              val result = performActionWithJsonBody(Json.toJson(companyMatchRequest))
              status(result)                                                  shouldBe OK
              contentAsJson(result).validate[HECTaxCheckMatchResult].asEither shouldBe Right(matchResult)
            }

          }

        }

      }

    }

    "handling requests to get unexpired tax check codes" must {
      val ggCredId = GGCredId("ggCredId")

      "return an 500 (internal server error)" when {

        "there is an error saving the tax check" in {
          mockAuthWithGGRetrieval(ggCredId.value)
          mockGetValidTaxCheckCodes(ggCredId)(Left(Error(new Exception("Oh no!"))))

          val result = controller.getUnexpiredTaxCheckCodes(FakeRequest())
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 200 (OK)" when {

        "the tax check service returns an empty list of codes" in {
          mockAuthWithGGRetrieval(ggCredId.value)
          mockGetValidTaxCheckCodes(ggCredId)(Right(List.empty))

          val result = controller.getUnexpiredTaxCheckCodes(FakeRequest())
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe JsArray(Seq.empty)
        }

        "the tax check service returns a list of codes" in {
          mockAuthWithGGRetrieval(ggCredId.value)
          val items = List(
            TaxCheckListItem(
              LicenceType.ScrapMetalDealerSite,
              HECTaxCheckCode("some-code"),
              LocalDate.now()
            )
          )
          mockGetValidTaxCheckCodes(ggCredId)(Right(items))

          val result = controller.getUnexpiredTaxCheckCodes(FakeRequest())
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe JsArray(items.map(Json.toJson[TaxCheckListItem]))
        }

      }
    }

  }

}
