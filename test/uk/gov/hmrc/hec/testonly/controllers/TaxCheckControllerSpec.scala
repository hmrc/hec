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

package uk.gov.hmrc.hec.testonly.controllers

import cats.data.EitherT
import cats.implicits.catsSyntaxOptionId
import cats.instances.future._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.hec.controllers.ControllerSpec
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.hecTaxCheck.ApplicantDetails.IndividualApplicantDetails
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.IndividualHECTaxCheckData
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxDetails.IndividualTaxDetails
import uk.gov.hmrc.hec.models.hecTaxCheck._
import uk.gov.hmrc.hec.models.ids.{CRN, GGCredId, NINO, SAUTR}
import uk.gov.hmrc.hec.models.licence.{LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.testonly.models.SaveTaxCheckRequest
import uk.gov.hmrc.hec.testonly.services.TaxCheckService
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxCheckControllerSpec extends ControllerSpec {

  val mockTaxCheckService = mock[TaxCheckService]

  override val overrideBindings =
    List[GuiceableModule](
      bind[TaxCheckService].toInstance(mockTaxCheckService)
    )

  def mockSaveTaxCheck(request: SaveTaxCheckRequest)(result: Either[Error, Unit]) =
    (mockTaxCheckService
      .saveTaxCheck(_: SaveTaxCheckRequest)(_: HeaderCarrier))
      .expects(request, *)
      .returning(EitherT.fromEither(result))

  def mockGetTaxCheck(taxCheckCode: HECTaxCheckCode)(result: Either[Error, Option[HECTaxCheck]]) =
    (mockTaxCheckService
      .getTaxCheck(_: HECTaxCheckCode)(_: HeaderCarrier))
      .expects(taxCheckCode, *)
      .returning(EitherT.fromEither(result))

  def mockDeleteTaxCheck(taxCheckCode: HECTaxCheckCode)(result: Either[Error, Unit]) =
    (mockTaxCheckService
      .deleteTaxCheck(_: HECTaxCheckCode)(_: HeaderCarrier))
      .expects(taxCheckCode, *)
      .returning(EitherT.fromEither(result))

  def mockDeleteAllTaxChecks(result: Either[Error, Unit]) =
    (mockTaxCheckService
      .deleteAllTaxCheck()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither(result))

  val taxCheckStartDateTime = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("Europe/London"))

  val controller = instanceOf[TaxCheckController]

  "TaxCheckController" when {

    "handling requests to save a tax check" must {

      def performActionWithJsonBody(requestBody: JsValue): Future[Result] = {
        val request = FakeRequest().withBody(requestBody).withHeaders(CONTENT_TYPE -> JSON)
        controller.saveTaxCheck(request)
      }

      def toJsonString(date: LocalDate): String = date.format(DateTimeFormatter.BASIC_ISO_DATE)

      def requestJsonString(
        r: SaveTaxCheckRequest
      ): String = {
        val verifierJson = r.verifier.fold(
          crn => s"""{ "crn" : "${crn.value}" }""",
          dob => s"""{ "dateofbirth" : "${toJsonString(dob.value)}" }"""
        )

        val getStartDateTime = r.taxCheckStartDateTime.toString

        s"""
           |{
           |  "taxCheckCode" : "${r.taxCheckCode.value}",
           |  "ggCredId" : "${r.ggCredId.value}",
           |  "licenceType" : "${r.licenceType.toString}",
           |  "verifier" : $verifierJson,
           |  "expiresAfter" : "${toJsonString(r.expiresAfter)}",
           |  "createDate" : "${r.createDate}",
           |  "taxCheckStartDateTime": "$getStartDateTime",
           |  "isExtracted": false,
           |  "source" : "${r.source.toString}",
           |  "relevantIncomeTaxYear": ${r.relevantIncomeTaxYear.map(_.startYear).getOrElse(2021)}
           |}
           |""".stripMargin
      }

      val validTaxCheckCode = HECTaxCheckCode("ABCABCABC")

      behave like invalidTaxCheckCodeBehaviour { invalidTaxCheckCode =>
        val dateOfBirth = DateOfBirth(TimeUtils.today())
        val request     = SaveTaxCheckRequest(
          HECTaxCheckCode(invalidTaxCheckCode),
          GGCredId("AB123"),
          LicenceType.DriverOfTaxisAndPrivateHires,
          Right(dateOfBirth),
          TimeUtils.today(),
          TimeUtils.now(),
          taxCheckStartDateTime,
          false,
          HECTaxCheckSource.Digital,
          TaxYear(2021).some,
          None
        )
        val body        = Json.parse(requestJsonString(request))

        performActionWithJsonBody(body)
      }

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

      "return a 500 (internal server error)" when {

        "there is an error saving the tax check" in {
          val dateOfBirth = DateOfBirth(TimeUtils.today())
          val request     = SaveTaxCheckRequest(
            taxCheckCode = validTaxCheckCode,
            GGCredId("AB123"),
            licenceType = LicenceType.DriverOfTaxisAndPrivateHires,
            verifier = Right(dateOfBirth),
            expiresAfter = TimeUtils.today(),
            createDate = TimeUtils.now(),
            taxCheckStartDateTime = taxCheckStartDateTime,
            isExtracted = false,
            HECTaxCheckSource.Digital,
            TaxYear(2021).some,
            None
          )
          val body        = Json.parse(requestJsonString(request))

          mockSaveTaxCheck(request)(Left(Error("")))

          val result = performActionWithJsonBody(body)
          println(s"${contentAsString(result)}\n\n")
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return an 201 (created)" when {

        "a tax check is saved for an individual" in {
          val dateOfBirth = DateOfBirth(TimeUtils.today())
          val request     = SaveTaxCheckRequest(
            validTaxCheckCode,
            GGCredId("AB123"),
            LicenceType.DriverOfTaxisAndPrivateHires,
            Right(dateOfBirth),
            TimeUtils.today(),
            TimeUtils.now(),
            taxCheckStartDateTime,
            false,
            HECTaxCheckSource.Digital,
            TaxYear(2021).some,
            None
          )
          val body        = Json.parse(requestJsonString(request))

          mockSaveTaxCheck(request)(Right(()))

          val result = performActionWithJsonBody(body)
          status(result) shouldBe CREATED
        }

        "a tax check is saved for a company" in {
          val crn     = CRN("1234567895")
          val request = SaveTaxCheckRequest(
            validTaxCheckCode,
            GGCredId("AB123"),
            LicenceType.DriverOfTaxisAndPrivateHires,
            Left(crn),
            TimeUtils.today(),
            TimeUtils.now(),
            taxCheckStartDateTime,
            false,
            HECTaxCheckSource.Digital,
            TaxYear(2021).some,
            None
          )
          val body    = Json.parse(requestJsonString(request))

          mockSaveTaxCheck(request)(Right(()))

          val result = performActionWithJsonBody(body)
          status(result) shouldBe CREATED
        }

      }

    }

    "handling requests to get a tax check" must {

      def performAction(taxCheckCode: String): Future[Result] =
        controller.getTaxCheck(taxCheckCode)(FakeRequest())

      val validTaxCheckCode = HECTaxCheckCode("ABCABCABC")

      behave like invalidTaxCheckCodeBehaviour(performAction)

      "return a 500 (internal server error)" when {

        "there is an error getting the tax check" in {
          mockGetTaxCheck(validTaxCheckCode)(Left(Error("")))

          val result = performAction(validTaxCheckCode.value)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 404 (not found)" when {

        "there is no tax check found" in {
          mockGetTaxCheck(validTaxCheckCode)(Right(None))

          val result = performAction(validTaxCheckCode.value)
          status(result)          shouldBe NOT_FOUND
          contentAsString(result) shouldBe "No tax check found"
        }

      }

      "return an 200 (ok)" when {

        "a tax check was found" in {
          val taxCheckData = IndividualHECTaxCheckData(
            IndividualApplicantDetails(Some(GGCredId("")), Name("", ""), DateOfBirth(LocalDate.now())),
            LicenceDetails(
              LicenceType.ScrapMetalDealerSite,
              LicenceTimeTrading.EightYearsOrMore,
              LicenceValidityPeriod.UpToOneYear
            ),
            IndividualTaxDetails(
              NINO(""),
              Some(SAUTR("")),
              TaxSituation.SAPAYE,
              Some(YesNoAnswer.Yes),
              None,
              TaxYear(2021),
              None
            ),
            taxCheckStartDateTime,
            HECTaxCheckSource.Digital
          )
          val taxCheck     =
            models.hecTaxCheck
              .HECTaxCheck(taxCheckData, validTaxCheckCode, TimeUtils.today(), TimeUtils.now(), false, None)

          mockGetTaxCheck(validTaxCheckCode)(Right(Some(taxCheck)))

          val result = performAction(validTaxCheckCode.value)
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(taxCheck)
        }

      }

    }

    "handling requests to delete a tax check" must {

      def performAction(taxCheckCode: String): Future[Result] =
        controller.deleteTaxCheck(taxCheckCode)(FakeRequest())

      val validTaxCheckCode = HECTaxCheckCode("ABCABCABC")

      behave like invalidTaxCheckCodeBehaviour(performAction)

      "return a 500 (internal server error)" when {

        "there is an error deleting the tax check" in {
          mockDeleteTaxCheck(validTaxCheckCode)(Left(Error("")))

          val result = performAction(validTaxCheckCode.value)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 200 (ok)" when {

        "deleting the tax check was successful" in {
          mockDeleteTaxCheck(validTaxCheckCode)(Right(()))

          val result = performAction(validTaxCheckCode.value)
          status(result) shouldBe OK
        }

      }

    }

    "handling requests to delete all tax checks" must {

      def performAction(): Future[Result] =
        controller.deleteAllTaxChecks()(FakeRequest())

      "return a 500 (internal server error)" when {

        "there is an error deleting the tax check" in {
          mockDeleteAllTaxChecks(Left(Error("")))

          val result = performAction()
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 200 (ok)" when {

        "deleting all the tax check was successful" in {
          mockDeleteAllTaxChecks(Right(()))

          val result = performAction()
          status(result) shouldBe OK
        }

      }

    }

  }

  def invalidTaxCheckCodeBehaviour(performAction: String => Future[Result]): Unit =
    "return a 400 (bad request)" when {

      def test(s: String): Unit = {
        val result = performAction(s)
        status(result) shouldBe BAD_REQUEST
      }

      "the code has less than 9 characters in it" in {
        test("ABC")
      }

      "the code has more than 9 characters in it" in {
        test("ABCABCABCA")
      }

      "the code includes spaces in it" in {
        test("ABC ABC ABC")
      }

      "the code contains an invalid character in it" in {
        test("ABCABCAB1")
      }

      "the code is all lower case" in {
        test("abcabcabc")
      }

    }

}
