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

import akka.actor.ActorSystem
import cats.data.EitherT
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.HECTaxCheck
import uk.gov.hmrc.hec.services.scheduleService.{HECTaxCheckExtractionContext, HecTaxCheckExtractionServiceImpl}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class HecTaxCheckExtractionServiceSpec extends AnyWordSpec with Matchers with MockFactory {
  val mockTaxCheckService     = mock[TaxCheckService]
  val mockMongoLockService    = mock[MongoLockService]
  val mockFileCreationService = mock[FileCreationService]
  val mockFileStoreService    = mock[FileStoreService]

  def getValue(data: Either[models.Error, List[HECTaxCheck]]) = Future.successful(data)

  def mockGetAlltaxCheckByExtractedStatus(isExtracted: Boolean)(result: Either[models.Error, List[HECTaxCheck]]) =
    (mockTaxCheckService
      .getAllTaxCheckCodesByExtractedStatus(_: Boolean)(_: HeaderCarrier))
      .expects(isExtracted, *)
      .returning(EitherT.fromEither[Future](result))

  @SuppressWarnings(Array("org.wartremover.warts.All"))
  def mockWithLock(lockObtained: Boolean) =
    //val f: Future[Either[models.Error, List[HECTaxCheck]]] = getValue(data)
    (mockMongoLockService
      .withLock(_: Future[Either[models.Error, List[HECTaxCheck]]])(_: HECTaxCheckExtractionContext))
      .expects(*, *)
      // .returning(Future.successful(result))
      .onCall { test =>
        if (lockObtained)
          test.productElement(0).asInstanceOf[() => Future[Either[models.Error, List[HECTaxCheck]]]]().map(Some(_))
        else
          Future.successful(None)
      }

  def mockUpdateAllHecTaxCheck(hecTaxCheckList: List[HECTaxCheck])(result: Either[models.Error, List[HECTaxCheck]]) =
    (mockTaxCheckService
      .updateAllHecTaxCheck(_: List[HECTaxCheck])(_: HeaderCarrier))
      .expects(hecTaxCheckList, *)
      .returning(EitherT.fromEither[Future](result))

  def mockCreateFileContent[A](inputType: A, seqNum: String, partialFileName: String)(
    result: Either[models.Error, (String, String)]
  ) =
    (mockFileCreationService
      .createFileContent[A](_: A, _: String, _: String))
      .expects(inputType, seqNum, partialFileName)
      .returning(EitherT.fromEither[Future](result))

  def mockStoreFile(fileContent: String, fileName: String, dirName: String)(result: Either[models.Error, Unit]) =
    (mockFileStoreService
      .storeFile(_: String, _: String, _: String)(_: HeaderCarrier, _: HECTaxCheckExtractionContext))
      .expects(fileContent, fileName, dirName, *, *)
      .returning(EitherT.fromEither[Future](result))

  implicit val sys: ActorSystem                                           = ActorSystem("MyTest")
  implicit val hecTaxCheckExtractionContext: HECTaxCheckExtractionContext = new HECTaxCheckExtractionContext(sys)

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      """
        | mongo-lock.force-lock-release-after = 30 minutes
        |""".stripMargin
    )
  )

  val hecTaxCheckExtractionService = new HecTaxCheckExtractionServiceImpl(
    mockTaxCheckService,
    mockMongoLockService,
    mockFileCreationService,
    mockFileStoreService
  )

  "HecTaxCheckExtractionServiceSpec" must {

    "return an error" when {
//      val taxCheckData  = CompanyHECTaxCheckData(
//        CompanyApplicantDetails(GGCredId(""), CRN("")),
//        LicenceDetails(
//          LicenceType.ScrapMetalDealerSite,
//          LicenceTimeTrading.EightYearsOrMore,
//          LicenceValidityPeriod.UpToOneYear
//        ),
//        CompanyTaxDetails(CTUTR(""))
//      )
//      val taxCheckCode1 = HECTaxCheckCode("code1")
//      val taxCheckCode2 = HECTaxCheckCode("code12")
//      val taxCheckCode3 = HECTaxCheckCode("code13")
//      val taxCheck1     = HECTaxCheck(taxCheckData, taxCheckCode1, TimeUtils.today(), TimeUtils.now(), false)
//      val taxCheck2     = taxCheck1.copy(taxCheckCode = taxCheckCode2)
//      val taxCheck3     = taxCheck1.copy(taxCheckCode = taxCheckCode3, isExtracted = true)
//
//      val hecTaxCheckList = List(taxCheck1, taxCheck2, taxCheck3)

      "there is an error in fetching data from mongo db" in {
        inSequence {
          mockWithLock(lockObtained = false)

//          mockGetAlltaxCheckByExtractedStatus(false)(Left(models.Error("")))
          //mockCreateFileContent("licenceTYpe", "001", "licence_type")(Left(models.Error("")))
        }
        val result: Future[Option[Either[models.Error, List[HECTaxCheck]]]] =
          hecTaxCheckExtractionService.lockAndExtractJob()

        await(result) shouldBe None
      }

      "there is an success in fetching data from mongo db" in {
        inSequence {
          mockWithLock(lockObtained = true)
          mockGetAlltaxCheckByExtractedStatus(false)(Left(models.Error("a")))
          //mockCreateFileContent("licenceTYpe", "001", "licence_type")(Left(models.Error("")))
        }
        val result: Future[Option[Either[models.Error, List[HECTaxCheck]]]] =
          hecTaxCheckExtractionService.lockAndExtractJob()

        await(result) shouldBe Some(Left(models.Error("a")))
      }

    }
  }

}
