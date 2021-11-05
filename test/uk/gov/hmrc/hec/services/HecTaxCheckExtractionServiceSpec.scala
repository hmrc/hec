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
import akka.testkit.TestKit
import cats.data.EitherT
import cats.instances.future._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.ApplicantDetails.CompanyApplicantDetails
import uk.gov.hmrc.hec.models.HECTaxCheckData.CompanyHECTaxCheckData
import uk.gov.hmrc.hec.models.TaxDetails.CompanyTaxDetails
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR, GGCredId}
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models._
import uk.gov.hmrc.hec.services.scheduleService.{HECTaxCheckExtractionContext, HecTaxCheckExtractionServiceImpl}
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.Future

class HecTaxCheckExtractionServiceSpec
    extends TestKit(
      ActorSystem(
        "hec-tax-check-extraction-service-impl",
        ConfigFactory
          .defaultApplication()
          .resolve()
          .withValue("akka.test.single-expect-default", ConfigValueFactory.fromAnyRef("3 seconds"))
      )
    )
    with AnyWordSpecLike
    with Matchers
    with MockFactory
    with BeforeAndAfterAll {
  val mockTaxCheckService     = mock[TaxCheckService]
  val mockMongoLockService    = mock[MongoLockService]
  val mockFileCreationService = mock[FileCreationService]
  val mockFileStoreService    = mock[FileStoreService]

  val taxCheckStartDateTime = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("Europe/London"))

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  def mockGetAlltaxCheckByExtractedStatus(isExtracted: Boolean)(result: Either[models.Error, List[HECTaxCheck]]) =
    (mockTaxCheckService
      .getAllTaxCheckCodesByExtractedStatus(_: Boolean)(_: HeaderCarrier))
      .expects(isExtracted, *)
      .returning(EitherT.fromEither[Future](result))

  @SuppressWarnings(Array("org.wartremover.warts.All"))
  def mockWithLock(lockObtained: Boolean) =
    (mockMongoLockService
      .withLock(_: Future[Either[models.Error, List[HECTaxCheck]]])(_: HECTaxCheckExtractionContext))
      .expects(*, *)
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

  def mockCreateFileContent[A](inputType: A, seqNum: String, partialFileName: String, isLastInSequence: Boolean)(
    result: Either[models.Error, (String, String)]
  ) =
    (mockFileCreationService
      .createFileContent[A](_: A, _: String, _: String, _: Boolean))
      .expects(inputType, seqNum, partialFileName, isLastInSequence)
      .returning(result)

  def mockStoreFile(fileContent: String, fileName: String, dirName: String)(result: Either[models.Error, Unit]) =
    (mockFileStoreService
      .storeFile(_: String, _: String, _: String)(_: HeaderCarrier, _: HECTaxCheckExtractionContext))
      .expects(fileContent, fileName, dirName, *, *)
      .returning(EitherT.fromEither[Future](result))

  implicit val hecTaxCheckExtractionContext: HECTaxCheckExtractionContext = new HECTaxCheckExtractionContext(system)

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      """
        | mongo-lock.force-lock-release-after = 30 minutes
        | hec-tax-heck-file.default-size = 2
        |""".stripMargin
    )
  )

  val hecTaxCheckExtractionService = new HecTaxCheckExtractionServiceImpl(
    mockTaxCheckService,
    mockMongoLockService,
    mockFileCreationService,
    mockFileStoreService,
    config
  )

  "HecTaxCheckExtractionServiceSpec" must {

    val taxCheckData  = CompanyHECTaxCheckData(
      CompanyApplicantDetails(GGCredId(""), CRN(""), CompanyHouseName("Test Tech Ltd")),
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
    val taxCheckCode1 = HECTaxCheckCode("code1")
    val taxCheckCode2 = HECTaxCheckCode("code12")
    val taxCheckCode3 = HECTaxCheckCode("code13")
    val taxCheck1     = HECTaxCheck(taxCheckData, taxCheckCode1, TimeUtils.today(), TimeUtils.now(), false, None, None)
    val taxCheck2     = taxCheck1.copy(taxCheckCode = taxCheckCode2)
    val taxCheck3     = taxCheck1.copy(taxCheckCode = taxCheckCode3, isExtracted = false)

    val hecTaxCheckList        = List(taxCheck1, taxCheck2, taxCheck3)
    val updatedHecTaxCheckList =
      List(taxCheck1.copy(isExtracted = true), taxCheck2.copy(isExtracted = true), taxCheck3.copy(isExtracted = true))

    val sdesDirectory = "sdes"

    "return None" when {

      " no lock is obtained on mongo db" in {
        inSequence {
          mockWithLock(lockObtained = false)
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()

        await(result) shouldBe None
      }

    }

    "return an error" when {

      "there is error in fetching data from mongo" in {
        inSequence {
          mockWithLock(lockObtained = true)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/licence-type")(
            Right(())
          )
          mockCreateFileContent(LicenceTimeTrading, "0001", "HEC_LICENCE_TIME_TRADING", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-time-trading"
          )(
            Right(())
          )
          mockCreateFileContent(LicenceValidityPeriod, "0001", "HEC_LICENCE_VALIDITY_PERIOD", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-validity-period"
          )(
            Right(())
          )
          mockCreateFileContent(CorrectiveAction, "0001", "HEC_CORRECTIVE_ACTION", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/corrective-action"
          )(
            Right(())
          )
          mockGetAlltaxCheckByExtractedStatus(false)(Left(models.Error("a")))
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Left(models.Error("a")))
      }

      "There is an error in file creation" in {
        inSequence {
          mockWithLock(lockObtained = true)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(Left(models.Error("err")))
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Left(models.Error("err")))
      }

      "There is an error in file storage" in {
        inSequence {
          mockWithLock(lockObtained = true)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/licence-type")(
            Left(models.Error("err"))
          )
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Left(models.Error("err")))
      }

      "There is an error in mongo record update" in {
        inSequence {
          mockWithLock(lockObtained = true)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/licence-type")(
            Right(())
          )
          mockCreateFileContent(LicenceTimeTrading, "0001", "HEC_LICENCE_TIME_TRADING", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-time-trading"
          )(
            Right(())
          )
          mockCreateFileContent(LicenceValidityPeriod, "0001", "HEC_LICENCE_VALIDITY_PERIOD", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-validity-period"
          )(
            Right(())
          )
          mockCreateFileContent(CorrectiveAction, "0001", "HEC_CORRECTIVE_ACTION", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/corrective-action"
          )(
            Right(())
          )
          mockGetAlltaxCheckByExtractedStatus(false)(Right(hecTaxCheckList.take(2)))
          mockCreateFileContent(HECTaxCheckFileBodyList(hecTaxCheckList.take(2)), "0001", "HEC", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/tax-checks")(
            Right(())
          )
          mockUpdateAllHecTaxCheck(updatedHecTaxCheckList.take(2))(Left(models.Error("err")))
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Left(models.Error("err")))
      }

    }

    "lock and process the hec data" when {

      "all fetch , update , file creation and storage passed without error, number of records equal to default process size" in {
        inSequence {
          mockWithLock(lockObtained = true)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/licence-type")(
            Right(())
          )
          mockCreateFileContent(LicenceTimeTrading, "0001", "HEC_LICENCE_TIME_TRADING", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-time-trading"
          )(
            Right(())
          )
          mockCreateFileContent(LicenceValidityPeriod, "0001", "HEC_LICENCE_VALIDITY_PERIOD", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-validity-period"
          )(
            Right(())
          )
          mockCreateFileContent(CorrectiveAction, "0001", "HEC_CORRECTIVE_ACTION", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/corrective-action"
          )(
            Right(())
          )
          mockGetAlltaxCheckByExtractedStatus(false)(Right(hecTaxCheckList.take(2)))
          mockCreateFileContent(HECTaxCheckFileBodyList(hecTaxCheckList.take(2)), "0001", "HEC", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/tax-checks")(
            Right(())
          )
          mockUpdateAllHecTaxCheck(updatedHecTaxCheckList.take(2))(Right(updatedHecTaxCheckList))
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Right(()))

      }

    }
  }

}
