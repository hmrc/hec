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

package uk.gov.hmrc.hec.controllers

import cats.data.EitherT
import cats.instances.future._
import com.github.ghik.silencer.silent
import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json._
import play.api.mvc.Result
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.DateOfBirth
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.LicenceType
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckCode
import uk.gov.hmrc.hec.models.ids._
import uk.gov.hmrc.hec.models.taxCheckMatch.{HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckMatchStatus, MatchFailureReason}
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.services.TaxCheckService
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@silent("deprecated")
class TaxCheckControllerWithInternalAuthDisabledSpec extends ControllerSpec with AuthSupport {

  val mockTaxCheckService = mock[TaxCheckService]

  val taxCheckStartDateTime = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("Europe/London"))

  val ggCredId    = GGCredId("ggCredId")
  implicit val cc = Helpers.stubControllerComponents()

  val mockInternalAuthStubBehaviour = mock[StubBehaviour]
  val mockBackendAuthComponents     = BackendAuthComponentsStub(mockInternalAuthStubBehaviour)

  override val overrideBindings =
    List[GuiceableModule](
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[TaxCheckService].toInstance(mockTaxCheckService),
      bind[BackendAuthComponents].toInstance(mockBackendAuthComponents)
    )

  val controller = instanceOf[TaxCheckController]

  override def additionalConfig = super.additionalConfig.withFallback(
    Configuration(
      ConfigFactory.parseString(
        s"""
           | internal-auth {
           |    enabled = false
           |}
           |""".stripMargin
      )
    )
  )

  def mockMatchTaxCheck(matchRequest: HECTaxCheckMatchRequest)(result: Either[Error, HECTaxCheckMatchResult]) =
    (mockTaxCheckService
      .matchTaxCheck(_: HECTaxCheckMatchRequest)(_: HeaderCarrier))
      .expects(matchRequest, *)
      .returning(EitherT.fromEither(result))

  "TaxCheckController" when {

    "handling requests to match a tax check" must {

      def performAActionWithJsonBodyAndHeader(requestBody: JsValue): Future[Result] = {
        val request = FakeRequest().withBody(requestBody).withHeaders(CONTENT_TYPE -> JSON, AUTHORIZATION -> "token")
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
          val result: Future[Result] =
            controller.matchTaxCheck(FakeRequest().withHeaders(CONTENT_TYPE -> TEXT, AUTHORIZATION -> "token")).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

        }

        "there is no json body in the request" in {
          val result: Future[Result] =
            controller
              .matchTaxCheck(FakeRequest().withBody("hi").withHeaders(CONTENT_TYPE -> TEXT, AUTHORIZATION -> "token"))
              .run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

        }

      }

      "return a 400 (bad request)" when {

        "the JSON in the request cannot be parsed" in {
          status(performAActionWithJsonBodyAndHeader(JsString("hi"))) shouldBe BAD_REQUEST
        }

      }

      "return an 500 (internal server error)" when {

        "there is an error saving the tax check" in {
          mockMatchTaxCheck(individualMatchRequest)(Left(Error(new Exception("Oh no!"))))

          val result = performAActionWithJsonBodyAndHeader(Json.toJson(individualMatchRequest))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 200 (OK)" when {

        "the tax check service returns a match result" in {

          val dateTime = TimeUtils.now()

          List[HECTaxCheckMatchResult](
            HECTaxCheckMatchResult(companyMatchRequest, dateTime, HECTaxCheckMatchStatus.Match),
            HECTaxCheckMatchResult(
              companyMatchRequest,
              dateTime,
              HECTaxCheckMatchStatus.NoMatch(MatchFailureReason.CRNNotMatched)
            ),
            HECTaxCheckMatchResult(companyMatchRequest, dateTime, HECTaxCheckMatchStatus.Expired)
          ).foreach { matchResult =>
            withClue(s"For match result '$matchResult': ") {
              mockMatchTaxCheck(companyMatchRequest)(Right(matchResult))

              val result = performAActionWithJsonBodyAndHeader(Json.toJson(companyMatchRequest))
              status(result)                                                  shouldBe OK
              contentAsJson(result).validate[HECTaxCheckMatchResult].asEither shouldBe Right(matchResult)
            }

          }

        }

      }

    }

  }

}
