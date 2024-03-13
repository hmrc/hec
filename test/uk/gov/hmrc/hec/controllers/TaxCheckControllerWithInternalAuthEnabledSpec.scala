/*
 * Copyright 2024 HM Revenue & Customs
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
import com.typesafe.config.ConfigFactory
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import play.api.Configuration
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json._
import play.api.mvc.{ControllerComponents, Request, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core.retrieve.{GGCredId => AuthGGCredId, Name => RetrievalName, OneTimeLogin, PAClientId}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.hec.controllers.actions.AuthenticatedGGOrStrideRequest
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.hecTaxCheck.ApplicantDetails.{CompanyApplicantDetails, IndividualApplicantDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxDetails.{CompanyTaxDetails, IndividualTaxDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck._
import uk.gov.hmrc.hec.models.hecTaxCheck.company.CTAccountingPeriod.CTAccountingPeriodDigital
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTStatus, CTStatusResponse, CompanyHouseName}
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.{DateOfBirth, Name}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.ids._
import uk.gov.hmrc.hec.models.taxCheckMatch.{HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckMatchStatus, MatchFailureReason}
import uk.gov.hmrc.hec.models.{EmailAddress, Error, SaveEmailAddressRequest, StrideOperatorDetails, TaxCheckListItem, taxCheckMatch}
import uk.gov.hmrc.hec.services.TaxCheckService
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.Retrieval.EmptyRetrieval
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client._

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxCheckControllerWithInternalAuthEnabledSpec extends ControllerSpec with AuthSupport {

  val mockTaxCheckService = mock[TaxCheckService]

  val taxCheckStartDateTime = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("Europe/London"))

  val ggCredId                          = GGCredId("ggCredId")
  implicit val cc: ControllerComponents = Helpers.stubControllerComponents()

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
           |    enabled = true
           |}
           |""".stripMargin
      )
    )
  )

  val expectedResource: Resource = Resource(ResourceType("hec"), ResourceLocation("hec/match-tax-check"))

  val expectedPredicate: Permission =
    Permission(expectedResource, IAAction("READ"))

  def mockSaveTaxCheck(taxCheckData: HECTaxCheckData, request: AuthenticatedGGOrStrideRequest[_])(
    result: Either[Error, HECTaxCheck]
  ) =
    (mockTaxCheckService
      .saveTaxCheck(_: HECTaxCheckData)(_: HeaderCarrier, _: AuthenticatedGGOrStrideRequest[_]))
      .expects(taxCheckData, *, request)
      .returning(EitherT.fromEither(result))

  def mockInternalAuth(predicate: Predicate)(result: Future[_]) =
    (mockInternalAuthStubBehaviour
      .stubAuth(_: Option[Predicate], _: Retrieval[_]))
      .expects(Some(predicate), EmptyRetrieval)
      .returning(result)

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

  def mockSaveEmailAddress(saveEmailAddressRequest: SaveEmailAddressRequest)(
    result: Either[Error, Option[Unit]]
  ) =
    (mockTaxCheckService
      .saveEmailAddress(_: SaveEmailAddressRequest)(_: HeaderCarrier))
      .expects(saveEmailAddressRequest, *)
      .returning(EitherT.fromEither(result))

  "TaxCheckController" when {

    "handling requests to save a tax check" must {

      def performAction[A](request: Request[JsValue]): Future[Result] =
        controller.saveTaxCheck(request)

      val taxCheckDataIndividual: HECTaxCheckData = IndividualHECTaxCheckData(
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
          TaxYear(2021),
          None
        ),
        taxCheckStartDateTime,
        HECTaxCheckSource.Digital,
        None,
        Some(true),
        None
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
            Some(CTAccountingPeriodDigital(LocalDate.of(2020, 10, 9), LocalDate.of(2021, 10, 9), CTStatus.ReturnFound))
          ),
          None,
          Some(YesNoAnswer.Yes),
          None
        ),
        taxCheckStartDateTime,
        HECTaxCheckSource.Digital,
        None,
        Some(false),
        Some(true)
      )

      implicit val mat: Materializer = NoMaterializer

      def requestWithJson(json: JsValue): Request[JsValue] =
        FakeRequest().withBody(json).withHeaders(CONTENT_TYPE -> JSON)

      "return a 415 (unsupported media type)" when {

        "there is no body in the request" in {
          val requestWithNoBody = AuthenticatedGGOrStrideRequest(Right(GGCredId("")), FakeRequest())

          val result: Future[Result] = controller.saveTaxCheck(requestWithNoBody).run()(mat)
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

        }

        "there is no json body in the request" in {
          val requestWithNoJsonBody =
            AuthenticatedGGOrStrideRequest(
              Right(GGCredId("AB123")),
              FakeRequest().withBody("hi").withHeaders(CONTENT_TYPE -> TEXT)
            )

          val result: Future[Result] =
            controller.saveTaxCheck(requestWithNoJsonBody).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE
        }

      }

      "return a 400 (bad request)" when {

        "the JSON in the request cannot be parsed" in {
          mockGGOrStrideAuth(AuthGGCredId(ggCredId.value), Enrolments(Set.empty), None, None)

          status(performAction(requestWithJson(JsString("hi")))) shouldBe BAD_REQUEST

        }

      }

      "return an 500 (internal server error)" when {

        "there is an error saving the tax check" in {
          val request              = requestWithJson(Json.toJson(taxCheckDataIndividual))
          val authenticatedRequest = AuthenticatedGGOrStrideRequest(Right(GGCredId("id")), request)

          inSequence {
            mockGGOrStrideAuth(AuthGGCredId("id"), Enrolments(Set.empty), None, None)
            mockSaveTaxCheck(taxCheckDataIndividual, authenticatedRequest)(Left(Error(new Exception("Oh no!"))))
          }

          val result = performAction(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return 403 (forbidden)" when {

        "there is no authenticated session" in {
          mockAuth(AuthProviders(GovernmentGateway, PrivilegedApplication), ggOrStrideAuthRetrievals)(
            Future.failed(InvalidBearerToken(""))
          )

          val result = performAction(requestWithJson(Json.toJson(taxCheckDataIndividual)))
          status(result) shouldBe FORBIDDEN
        }

        "there is no gg cred id or privileged application id found" in {
          mockGGOrStrideAuth(OneTimeLogin, Enrolments(Set.empty), None, None)

          val result = performAction(requestWithJson(Json.toJson(taxCheckDataIndividual)))
          status(result) shouldBe FORBIDDEN
        }

      }

      "return a created (201)" when {

        "the tax check has been saved for individual" in {
          val taxCheckCode         = HECTaxCheckCode("code")
          val expiresAfterDate     = LocalDate.MIN
          val taxCheck             =
            models.hecTaxCheck
              .HECTaxCheck(taxCheckDataIndividual, taxCheckCode, expiresAfterDate, TimeUtils.now(), false, None, None)
          val request              = requestWithJson(Json.toJson(taxCheckDataIndividual))
          val authenticatedRequest = AuthenticatedGGOrStrideRequest(Right(ggCredId), request)

          inSequence {
            mockGGOrStrideAuth(AuthGGCredId(ggCredId.value), Enrolments(Set.empty), None, None)
            mockSaveTaxCheck(taxCheckDataIndividual, authenticatedRequest)(Right(taxCheck))
          }

          val result = performAction(request)
          status(result)                              shouldBe CREATED
          contentAsJson(result).validate[HECTaxCheck] shouldBe JsSuccess(taxCheck)
        }

        "the tax check has been saved for company" in {
          val taxCheckCode         = HECTaxCheckCode("code")
          val expiresAfterDate     = LocalDate.MIN
          val taxCheck             =
            models.hecTaxCheck
              .HECTaxCheck(taxCheckDataCompany, taxCheckCode, expiresAfterDate, TimeUtils.now(), false, None, None)
          val request              = requestWithJson(Json.toJson(taxCheckDataCompany))
          val authenticatedRequest = AuthenticatedGGOrStrideRequest(Right(ggCredId), request)

          inSequence {
            mockGGOrStrideAuth(AuthGGCredId(ggCredId.value), Enrolments(Set.empty), None, None)
            mockSaveTaxCheck(taxCheckDataCompany, authenticatedRequest)(Right(taxCheck))
          }

          val result = performAction(request)
          status(result)                              shouldBe CREATED
          contentAsJson(result).validate[HECTaxCheck] shouldBe JsSuccess(taxCheck)
        }

        "the request has come from a stride operator" when {

          def test(pid: PID, enrolments: Enrolments, name: Option[RetrievalName], email: Option[String])(
            expectedStrideOperatorDetails: StrideOperatorDetails
          ) = {
            val taxCheckCode         = HECTaxCheckCode("code")
            val expiresAfterDate     = LocalDate.MIN
            val taxCheck             =
              models.hecTaxCheck
                .HECTaxCheck(taxCheckDataIndividual, taxCheckCode, expiresAfterDate, TimeUtils.now(), false, None, None)
            val request              = requestWithJson(Json.toJson(taxCheckDataIndividual))
            val authenticatedRequest = AuthenticatedGGOrStrideRequest(Left(expectedStrideOperatorDetails), request)

            inSequence {
              mockGGOrStrideAuth(PAClientId(pid.value), enrolments, name, email)
              mockSaveTaxCheck(taxCheckDataIndividual, authenticatedRequest)(Right(taxCheck))
            }

            val result = performAction(request)
            status(result)                              shouldBe CREATED
            contentAsJson(result).validate[HECTaxCheck] shouldBe JsSuccess(taxCheck)
          }

          "all operator details are available" in {
            test(
              PID("pid"),
              Enrolments(Set(Enrolment("role1"))),
              Some(RetrievalName(Some("first"), Some("last"))),
              Some("email")
            )(
              StrideOperatorDetails(
                PID("pid"),
                List("role1"),
                Some("first last"),
                Some("email")
              )
            )
          }

          "both the first and last name of the operator are not available" in {
            List(
              Some(RetrievalName(None, None)),
              None
            ).foreach { emptyName =>
              withClue(s"For $emptyName: ") {
                test(
                  PID("pid"),
                  Enrolments(Set(Enrolment("role1"))),
                  emptyName,
                  Some("email")
                )(
                  StrideOperatorDetails(
                    PID("pid"),
                    List("role1"),
                    None,
                    Some("email")
                  )
                )
              }

            }
          }

          "the last name of the operator is not available" in {
            test(
              PID("pid"),
              Enrolments(Set(Enrolment("role1"))),
              Some(RetrievalName(Some("first"), None)),
              Some("email")
            )(
              StrideOperatorDetails(
                PID("pid"),
                List("role1"),
                Some("first"),
                Some("email")
              )
            )
          }

          "the first name of the operator is not available" in {
            test(
              PID("pid"),
              Enrolments(Set(Enrolment("role1"), Enrolment("role2"))),
              Some(RetrievalName(None, Some("last"))),
              Some("email")
            )(
              StrideOperatorDetails(
                PID("pid"),
                List("role1", "role2"),
                Some("last"),
                Some("email")
              )
            )
          }

        }

      }

    }

    "handling requests to match a tax check" must {

      def performAActionWithJsonBodyAndHeader(requestBody: JsValue): Future[Result] = {
        val request = FakeRequest().withBody(requestBody).withHeaders(CONTENT_TYPE -> JSON, AUTHORIZATION -> "token")
        controller.matchTaxCheck(request)
      }

      def performAActionWithJsonBody(requestBody: JsValue): Future[Result] = {
        val request = FakeRequest().withBody(requestBody)
        controller.matchTaxCheck(request)
      }

      val taxCheckCode = HECTaxCheckCode("code")
      val licenceType  = LicenceType.ScrapMetalDealerSite
      val dateOfBirth  = DateOfBirth(LocalDate.now())
      val crn          = CRN("crn")

      val individualMatchRequest: HECTaxCheckMatchRequest =
        taxCheckMatch.HECTaxCheckMatchRequest(taxCheckCode, licenceType, Right(dateOfBirth))

      val companyMatchRequest: HECTaxCheckMatchRequest =
        taxCheckMatch.HECTaxCheckMatchRequest(taxCheckCode, licenceType, Left(crn))

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
          mockInternalAuth(expectedPredicate)(Future.successful(()))
          status(performAActionWithJsonBodyAndHeader(JsString("hi"))) shouldBe BAD_REQUEST
        }

      }

      "return a 500 (internal server error)" when {

        "there is an error saving the tax check" in {
          mockInternalAuth(expectedPredicate)(Future.successful(()))
          mockMatchTaxCheck(individualMatchRequest)(Left(Error(new Exception("Oh no!"))))

          val result = performAActionWithJsonBodyAndHeader(Json.toJson(individualMatchRequest))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 401 (Unauthorised)" when {

        "there is no authenticated session" in {
          mockInternalAuth(expectedPredicate)(
            Future.failed(UpstreamErrorResponse.apply("Unauthorized", Status.UNAUTHORIZED))
          )
          intercept[UpstreamErrorResponse](
            await(performAActionWithJsonBodyAndHeader(Json.toJson(companyMatchRequest)))
          ).statusCode shouldBe UNAUTHORIZED
        }

        "there is no header in  session" in {
          intercept[UpstreamErrorResponse](
            await(performAActionWithJsonBody(Json.toJson(companyMatchRequest)))
          ).statusCode shouldBe UNAUTHORIZED
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
              HECTaxCheckMatchStatus.NoMatch(MatchFailureReason.LicenceTypeDateOfBirthNotMatched)
            ),
            HECTaxCheckMatchResult(companyMatchRequest, dateTime, HECTaxCheckMatchStatus.Expired)
          ).foreach { matchResult =>
            withClue(s"For match result '$matchResult': ") {
              mockInternalAuth(expectedPredicate)(Future.successful(()))
              mockMatchTaxCheck(companyMatchRequest)(Right(matchResult))

              val result = performAActionWithJsonBodyAndHeader(Json.toJson(companyMatchRequest))
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

    "handling requests to save an email address" must {

      def performAActionWithJsonBody(requestBody: JsValue): Future[Result] = {
        val request = FakeRequest().withBody(requestBody)
        controller.saveEmailAddress(request)
      }

      val saveEmailAddressRequest = SaveEmailAddressRequest(EmailAddress("email"), HECTaxCheckCode("code"))

      "return a 415 (unsupported media type)" when {

        "there is no body in the request" in {
          val requestWithNoBody = AuthenticatedGGOrStrideRequest(Right(GGCredId("")), FakeRequest())

          val result: Future[Result] = controller.saveEmailAddress(requestWithNoBody).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE

        }

        "there is no json body in the request" in {
          val requestWithNoJsonBody =
            AuthenticatedGGOrStrideRequest(
              Right(GGCredId("AB123")),
              FakeRequest().withBody("hi").withHeaders(CONTENT_TYPE -> TEXT)
            )

          val result: Future[Result] =
            controller.saveEmailAddress(requestWithNoJsonBody).run()
          status(result) shouldBe UNSUPPORTED_MEDIA_TYPE
        }

      }

      "return a 400 (bad request)" when {

        "the JSON in the request cannot be parsed" in {
          mockGGAuthWithGGRetrieval(ggCredId.value)

          status(performAActionWithJsonBody(JsString("hi"))) shouldBe BAD_REQUEST

        }

      }

      "return a 500 (internal server error)" when {

        "the call to save the email address returns an error" in {
          inSequence {
            mockGGAuthWithGGRetrieval(ggCredId.value)
            mockSaveEmailAddress(saveEmailAddressRequest)(Left(Error("")))
          }

          status(performAActionWithJsonBody(Json.toJson(saveEmailAddressRequest))) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 404 (not found)" when {

        "the call to save the email address returns None" in {
          inSequence {
            mockGGAuthWithGGRetrieval(ggCredId.value)
            mockSaveEmailAddress(saveEmailAddressRequest)(Right(None))
          }

          status(performAActionWithJsonBody(Json.toJson(saveEmailAddressRequest))) shouldBe NOT_FOUND
        }

      }

      "return a 200 (ok)" when {

        "the call to save the email address returns Some" in {
          inSequence {
            mockGGAuthWithGGRetrieval(ggCredId.value)
            mockSaveEmailAddress(saveEmailAddressRequest)(Right(Some(())))
          }

          status(performAActionWithJsonBody(Json.toJson(saveEmailAddressRequest))) shouldBe OK
        }

      }

    }

  }

}
