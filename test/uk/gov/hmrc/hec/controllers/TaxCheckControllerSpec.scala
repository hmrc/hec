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
import cats.implicits.catsSyntaxOptionId
import cats.instances.future._
import com.github.ghik.silencer.silent
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json._
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, InvalidBearerToken}
import uk.gov.hmrc.auth.core.retrieve.{PAClientId, VerifyPid, v2, GGCredId => AuthGGCredId}
import uk.gov.hmrc.hec.controllers.actions.AuthenticatedGGOrStrideRequest
import uk.gov.hmrc.hec.models.ApplicantDetails.{CompanyApplicantDetails, IndividualApplicantDetails}
import uk.gov.hmrc.hec.models.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.TaxDetails.{CompanyTaxDetails, IndividualTaxDetails}
import uk.gov.hmrc.hec.models.ids._
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.{CTAccountingPeriod, CTStatus, CTStatusResponse, CompanyHouseName, DateOfBirth, Error, HECTaxCheck, HECTaxCheckCode, HECTaxCheckData, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckMatchStatus, HECTaxCheckSource, Name, TaxCheckListItem, TaxSituation, TaxYear, YesNoAnswer}
import uk.gov.hmrc.hec.services.TaxCheckService
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@silent("deprecated")
class TaxCheckControllerSpec extends ControllerSpec with AuthSupport {

  val mockTaxCheckService = mock[TaxCheckService]

  override val overrideBindings =
    List[GuiceableModule](
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[TaxCheckService].toInstance(mockTaxCheckService)
    )

  val taxCheckStartDateTime = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("Europe/London"))

  val controller = instanceOf[TaxCheckController]
  val ggCredId   = GGCredId("ggCredId")

  def mockSaveTaxCheck(taxCheckData: HECTaxCheckData)(
    result: Either[Error, HECTaxCheck]
  ) =
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

      def performActionWithJsonBody(requestBody: JsValue): Future[Result] =
        controller.saveTaxCheck(FakeRequest().withBody(requestBody).withHeaders(CONTENT_TYPE -> JSON))

      val taxCheckDataIndividual: HECTaxCheckData                         = IndividualHECTaxCheckData(
        IndividualApplicantDetails(Some(GGCredId("")), Name("", ""), DateOfBirth(LocalDate.now())),
        LicenceDetails(
          LicenceType.ScrapMetalDealerSite,
          LicenceTimeTrading.EightYearsOrMore,
          LicenceValidityPeriod.UpToOneYear
        ),
        IndividualTaxDetails(
          NINO(""),
          Some(SAUTR("")),
          TaxSituation.PAYE,
          None,
          None,
          TaxYear(2021)
        ),
        taxCheckStartDateTime,
        HECTaxCheckSource.Digital
      )

      val taxCheckDataCompany: HECTaxCheckData = CompanyHECTaxCheckData(
        CompanyApplicantDetails(GGCredId("").some, CRN("12345678"), CompanyHouseName("Test Tech Ltd")),
        LicenceDetails(
          LicenceType.ScrapMetalDealerSite,
          LicenceTimeTrading.EightYearsOrMore,
          LicenceValidityPeriod.UpToOneYear
        ),
        CompanyTaxDetails(
          CTUTR("1111111111"),
          Some(CTUTR("1111111111")),
          Some(YesNoAnswer.Yes),
          CTStatusResponse(
            CTUTR("1111111111"),
            LocalDate.of(2020, 10, 9),
            LocalDate.of(2021, 10, 9),
            Some(CTAccountingPeriod(LocalDate.of(2020, 10, 9), LocalDate.of(2021, 10, 9), CTStatus.ReturnFound))
          ),
          None,
          Some(YesNoAnswer.Yes)
        ),
        taxCheckStartDateTime,
        HECTaxCheckSource.Digital
      )

      "return a 415 (unsupported media type)" when {

        "there is no body in the request" in {
          val requestWithNoBody = AuthenticatedGGOrStrideRequest(None, FakeRequest())

          val result: Future[Result] = controller.saveTaxCheck(requestWithNoBody).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

        }

        "there is no json body in the request" in {
          val requestWithNoJsonBody =
            AuthenticatedGGOrStrideRequest(
              Some("AB123"),
              FakeRequest().withBody("hi").withHeaders(CONTENT_TYPE -> TEXT)
            )

          val result: Future[Result] =
            controller.saveTaxCheck(requestWithNoJsonBody).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE
        }

      }

      "return a 400 (bad request)" when {

        "the JSON in the request cannot be parsed" in {
          mockGGOrStrideAuth(AuthGGCredId(ggCredId.value))

          status(performActionWithJsonBody(JsString("hi"))) shouldBe BAD_REQUEST

        }

      }

      "return an 500 (internal server error)" when {

        "there is an error saving the tax check" in {
          inSequence {
            mockGGOrStrideAuth(PAClientId(""))
            mockSaveTaxCheck(taxCheckDataIndividual)(Left(Error(new Exception("Oh no!"))))
          }

          val result = performActionWithJsonBody(Json.toJson(taxCheckDataIndividual))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return 403(forbidden)" when {

        "there is no authenticated session" in {
          mockAuth(AuthProviders(GovernmentGateway, PrivilegedApplication), v2.Retrievals.authProviderId)(
            Future.failed(InvalidBearerToken(""))
          )

          val result = performActionWithJsonBody(Json.toJson(taxCheckDataIndividual))
          status(result) shouldBe FORBIDDEN
        }

        "there is no gg cred id or privleged application id found" in {
          mockGGOrStrideAuth(VerifyPid(""))

          val result = performActionWithJsonBody(Json.toJson(taxCheckDataIndividual))
          status(result) shouldBe FORBIDDEN
        }

      }

      "return a created (201)" when {

        "the tax check has been saved for individual" in {
          val taxCheckCode     = HECTaxCheckCode("code")
          val expiresAfterDate = LocalDate.MIN
          val taxCheck         =
            HECTaxCheck(taxCheckDataIndividual, taxCheckCode, expiresAfterDate, TimeUtils.now(), false, None, None)

          inSequence {
            mockGGOrStrideAuth(AuthGGCredId(ggCredId.value))
            mockSaveTaxCheck(taxCheckDataIndividual)(Right(taxCheck))
          }

          val result = performActionWithJsonBody(Json.toJson(taxCheckDataIndividual))
          status(result)                              shouldBe CREATED
          contentAsJson(result).validate[HECTaxCheck] shouldBe JsSuccess(taxCheck)
        }

        "the tax check has been saved for company" in {
          val taxCheckCode     = HECTaxCheckCode("code")
          val expiresAfterDate = LocalDate.MIN
          val taxCheck         =
            HECTaxCheck(taxCheckDataCompany, taxCheckCode, expiresAfterDate, TimeUtils.now(), false, None, None)

          inSequence {
            mockGGOrStrideAuth(PAClientId(""))
            mockSaveTaxCheck(taxCheckDataCompany)(Right(taxCheck))
          }

          val result = performActionWithJsonBody(Json.toJson(taxCheckDataCompany))
          status(result)                              shouldBe CREATED
          contentAsJson(result).validate[HECTaxCheck] shouldBe JsSuccess(taxCheck)
        }

      }

    }

    "handling requests to match a tax check" must {

      def performAActionWithJsonBody(requestBody: JsValue): Future[Result] = {
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
          status(performAActionWithJsonBody(JsString("hi"))) shouldBe BAD_REQUEST

        }

      }

      "return an 500 (internal server error)" when {

        "there is an error saving the tax check" in {
          mockMatchTaxCheck(individualMatchRequest)(Left(Error(new Exception("Oh no!"))))

          val result = performAActionWithJsonBody(Json.toJson(individualMatchRequest))
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

              val result = performAActionWithJsonBody(Json.toJson(companyMatchRequest))
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
          mockGGAuthWithGGRetrieval(ggCredId.value)
          mockGetValidTaxCheckCodes(ggCredId)(Left(Error(new Exception("Oh no!"))))

          val result = controller.getUnexpiredTaxCheckCodes(FakeRequest())
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 200 (OK)" when {

        "the tax check service returns an empty list of codes" in {
          mockGGAuthWithGGRetrieval(ggCredId.value)
          mockGetValidTaxCheckCodes(ggCredId)(Right(List.empty))

          val result = controller.getUnexpiredTaxCheckCodes(FakeRequest())
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe JsArray(Seq.empty)
        }

        "the tax check service returns a list of codes" in {
          mockGGAuthWithGGRetrieval(ggCredId.value)
          val items = List(
            TaxCheckListItem(
              LicenceType.ScrapMetalDealerSite,
              HECTaxCheckCode("some-code"),
              LocalDate.now(),
              ZonedDateTime.now()
            )
          )
          mockGetValidTaxCheckCodes(ggCredId)(Right(items))

          val result = controller.getUnexpiredTaxCheckCodes(FakeRequest())
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe JsArray(items.map(Json.toJson[TaxCheckListItem]))
        }

      }

      "return 403(forbidden) , if not authenticated" in {

        mockGGAuthWithForbidden()
        val result = controller.getUnexpiredTaxCheckCodes(FakeRequest())
        status(result) shouldBe FORBIDDEN
      }
    }

  }

}
