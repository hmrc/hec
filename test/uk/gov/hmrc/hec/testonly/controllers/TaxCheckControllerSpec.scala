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

import cats.instances.future._
import cats.data.EitherT
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.hec.controllers.ControllerSpec
import uk.gov.hmrc.hec.models.{DateOfBirth, Error, HECTaxCheckCode}
import uk.gov.hmrc.hec.models.ids.CRN
import uk.gov.hmrc.hec.models.licence.LicenceType
import uk.gov.hmrc.hec.testonly.services.TaxCheckService
import uk.gov.hmrc.hec.testonly.models.SaveTaxCheckRequest
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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

        s"""
           |{
           |  "taxCheckCode" : "${r.taxCheckCode.value}",
           |  "licenceType" : { "${r.licenceType.toString}" : { } },
           |  "verifier" : $verifierJson,
           |  "expiresAfter" : "${toJsonString(r.expiresAfter)}"
           |}
           |""".stripMargin
      }

      val taxCheckCode = HECTaxCheckCode("ABCABCABC")

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
            taxCheckCode,
            LicenceType.DriverOfTaxisAndPrivateHires,
            Right(dateOfBirth),
            TimeUtils.today()
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
            taxCheckCode,
            LicenceType.DriverOfTaxisAndPrivateHires,
            Right(dateOfBirth),
            TimeUtils.today()
          )
          val body        = Json.parse(requestJsonString(request))

          mockSaveTaxCheck(request)(Right(()))

          val result = performActionWithJsonBody(body)
          status(result) shouldBe CREATED
        }

        "a tax check is saved for a company" in {
          val crn     = CRN("1234567895")
          val request = SaveTaxCheckRequest(
            taxCheckCode,
            LicenceType.DriverOfTaxisAndPrivateHires,
            Left(crn),
            TimeUtils.today()
          )
          val body    = Json.parse(requestJsonString(request))

          mockSaveTaxCheck(request)(Right(()))

          val result = performActionWithJsonBody(body)
          status(result) shouldBe CREATED
        }

      }

    }

  }

}
