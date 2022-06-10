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
import cats.implicits.catsSyntaxOptionId
import cats.instances.future._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json._
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.hecTaxCheck.ApplicantDetails.CompanyApplicantDetails
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.CompanyHECTaxCheckData
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxDetails.CompanyTaxDetails
import uk.gov.hmrc.hec.models.hecTaxCheck._
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTStatusResponse, CompanyHouseName}
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR, GGCredId}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.sdes.NotificationStatus._
import uk.gov.hmrc.hec.models.sdes.{CallBackNotification, NotificationStatus}
import uk.gov.hmrc.hec.models.{Error, hecTaxCheck}
import uk.gov.hmrc.hec.services.scheduleService.HECTaxCheckExtractionContext
import uk.gov.hmrc.hec.services.{FileStoreService, TaxCheckService}
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SDESCallbackControllerSpec extends ControllerSpec {

  val mockFileStoreService = mock[FileStoreService]
  val mockTaxCheckService  = mock[TaxCheckService]

  def mockDeleteFile(fileName: String, dirName: String)(result: Either[Error, Unit]) = (mockFileStoreService
    .deleteFile(_: String, _: String)(_: HeaderCarrier, _: HECTaxCheckExtractionContext))
    .expects(fileName, dirName, *, *)
    .returning(EitherT.fromEither(result))

  def mockGetAllTaxCheckCodesByCorrelationId(correlationId: UUID)(result: Either[Error, List[HECTaxCheck]]) =
    (mockTaxCheckService
      .getAllTaxCheckCodesByFileCorrelationId(_: String)(_: HeaderCarrier))
      .expects(correlationId.toString, *)
      .returning(EitherT.fromEither(result))

  def mockUpdateAllHecTaxCheck(hecTaxCheckList: List[HECTaxCheck])(result: Either[models.Error, List[HECTaxCheck]]) =
    (mockTaxCheckService
      .updateAllHecTaxCheck(_: List[HECTaxCheck])(_: HeaderCarrier))
      .expects(hecTaxCheckList, *)
      .returning(EitherT.fromEither[Future](result))

  override val overrideBindings =
    List[GuiceableModule](
      bind[FileStoreService].toInstance(mockFileStoreService),
      bind[TaxCheckService].toInstance(mockTaxCheckService)
    )
  val controller                = instanceOf[SDESCallbackController]

  val uuid = UUID.randomUUID()

  def createCallBackNotification(
    status: NotificationStatus,
    fileName: String
  ) = CallBackNotification(
    status,
    fileName,
    uuid.toString,
    None
  )

  private val now                   = TimeUtils.now()
  private val today                 = TimeUtils.today()
  val ggCredId                      = GGCredId("ggCredId")
  val taxCheckStartDateTime         = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("Europe/London"))
  val taxCheckData: HECTaxCheckData = CompanyHECTaxCheckData(
    CompanyApplicantDetails(ggCredId.some, CRN(""), CompanyHouseName("Test Tech Ltd")),
    LicenceDetails(
      LicenceType.ScrapMetalDealerSite,
      LicenceTimeTrading.EightYearsOrMore,
      LicenceValidityPeriod.UpToOneYear
    ),
    CompanyTaxDetails(
      CTUTR("1111111111"),
      Some(CTUTR("1111111111")),
      Some(YesNoAnswer.Yes),
      CTStatusResponse(CTUTR("1111111111"), LocalDate.of(2020, 10, 9), LocalDate.of(2021, 10, 9), None),
      None,
      Some(YesNoAnswer.Yes),
      None
    ),
    taxCheckStartDateTime,
    HECTaxCheckSource.Digital,
    None,
    None
  )

  val hecTaxCheck1 =
    hecTaxCheck.HECTaxCheck(
      taxCheckData,
      HECTaxCheckCode("ABC 123 ABC"),
      today.plusDays(1),
      now,
      false,
      uuid.some,
      None
    )
  val hecTaxCheck2 =
    hecTaxCheck.HECTaxCheck(
      taxCheckData,
      HECTaxCheckCode("EBC 123 ABC"),
      today.plusDays(1),
      now,
      false,
      uuid.some,
      None
    )

  val fileName1 = "HEC_SSA_0001_20210908_HEC_LICENCE_TYPE.dat"
  val fileName2 = "HEC_SSA_0001_20210908_HEC_APPLICATION.dat"

  "SDESCallbackControllerSpec" when {

    "handling request to callback from SDES" must {

      def performActionWithJsonBody(requestBody: JsValue): Future[Result] = {
        val request = FakeRequest().withBody(requestBody).withHeaders(CONTENT_TYPE -> JSON)
        controller.callback(request)
      }

      "return a 400 (bad request)" when {

        "the JSON in the call back cannot be parsed" in {
          status(performActionWithJsonBody(JsString("hi"))) shouldBe BAD_REQUEST
        }
      }

      "return a 500( internal server error)" when {

        "file deletion from object store fails" in {
          mockDeleteFile(fileName1, "sdes/licence-type")(Left(Error("err")))
          val notification =
            createCallBackNotification(FileProcessingFailure, fileName1)
          val result       = performActionWithJsonBody(Json.toJson(notification))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

        "call to fetch records from mongo db fails" in {
          mockDeleteFile(fileName2, "sdes/tax-checks")(Right(()))
          mockGetAllTaxCheckCodesByCorrelationId(uuid)(Left(Error("err")))
          val notification =
            createCallBackNotification(FileProcessed, fileName2)
          val result       = performActionWithJsonBody(Json.toJson(notification))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

        "call to update hec tax checks records fails" in {
          mockDeleteFile(fileName2, "sdes/tax-checks")(Right(()))
          mockGetAllTaxCheckCodesByCorrelationId(uuid)(Right(List(hecTaxCheck1, hecTaxCheck2)))
          mockUpdateAllHecTaxCheck(
            List(
              hecTaxCheck1.copy(fileCorrelationId = None, isExtracted = true),
              hecTaxCheck2.copy(fileCorrelationId = None, isExtracted = true)
            )
          )(Left(Error("err")))
          val notification =
            createCallBackNotification(FileProcessed, fileName2)
          val result       = performActionWithJsonBody(Json.toJson(notification))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return 200" when {

        "call back status is FileReady, no data processing" in {
          val notification =
            createCallBackNotification(FileReady, fileName1)
          val result       = performActionWithJsonBody(Json.toJson(notification))
          status(result) shouldBe OK
        }

        "call back status is FileReceived, no data processing" in {
          val notification =
            createCallBackNotification(FileReceived, fileName1)
          val result       = performActionWithJsonBody(Json.toJson(notification))
          status(result) shouldBe OK
        }

        "call back status is FileProcessingFailure, delete the file from object store" in {
          mockDeleteFile(fileName1, "sdes/licence-type")(Right(()))
          val notification =
            createCallBackNotification(FileProcessingFailure, fileName1)
          val result       = performActionWithJsonBody(Json.toJson(notification))
          status(result) shouldBe OK
        }

        "call back status is FileProcessed and " when {

          "and the file is an enum file, only delete the file" in {
            mockDeleteFile(fileName1, "sdes/licence-type")(Right(()))
            val notification =
              createCallBackNotification(FileProcessed, fileName1)
            val result       = performActionWithJsonBody(Json.toJson(notification))
            status(result) shouldBe OK
          }

          "and the file is hec tax check file,delete the file and update the mongo db" in {

            val updatedList  = List(
              hecTaxCheck1.copy(fileCorrelationId = None, isExtracted = true),
              hecTaxCheck2.copy(fileCorrelationId = None, isExtracted = true)
            )
            mockDeleteFile(fileName2, "sdes/tax-checks")(Right(()))
            mockGetAllTaxCheckCodesByCorrelationId(uuid)(Right(List(hecTaxCheck1, hecTaxCheck2)))
            mockUpdateAllHecTaxCheck(updatedList)(Right(updatedList))
            val notification =
              createCallBackNotification(FileProcessed, fileName2)
            val result       = performActionWithJsonBody(Json.toJson(notification))
            status(result) shouldBe OK
          }

        }

      }

    }

  }
}
