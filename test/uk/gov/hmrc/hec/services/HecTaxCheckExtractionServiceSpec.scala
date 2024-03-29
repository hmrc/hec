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

package uk.gov.hmrc.hec.services

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models._
import uk.gov.hmrc.hec.models.hecTaxCheck.ApplicantDetails.CompanyApplicantDetails
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.CompanyHECTaxCheckData
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxDetails.CompanyTaxDetails
import uk.gov.hmrc.hec.models.hecTaxCheck._
import uk.gov.hmrc.hec.models.hecTaxCheck.company.CTAccountingPeriod.CTAccountingPeriodDigital
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTStatus, CTStatusResponse, CompanyHouseName}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR, GGCredId}
import uk.gov.hmrc.hec.models.sdes._
import uk.gov.hmrc.hec.services.scheduleService.{HECTaxCheckExtractionContext, HecTaxCheckExtractionServiceImpl}
import uk.gov.hmrc.hec.util.{TimeUtils, UUIDGenerator}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.Path.{Directory, File}
import uk.gov.hmrc.objectstore.client.{Md5Hash, ObjectSummaryWithMd5}

import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.Future

class HecTaxCheckExtractionServiceSpec
    extends TestKit(
      ActorSystem(
        "hec-tax-check-extraction-service-impl",
        ConfigFactory
          .defaultApplication()
          .resolve()
          .withValue("org.apache.pekko.test.single-expect-default", ConfigValueFactory.fromAnyRef("3 seconds"))
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
  val mockUUIDGenerator       = mock[UUIDGenerator]
  val mockSDESService         = mock[SDESService]

  val taxCheckStartDateTime = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("Europe/London"))

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  def mockGetAlltaxCheckByExtractedStatus(isExtracted: Boolean, skip: Int, limit: Int, sortBy: String)(
    result: Either[models.Error, List[HECTaxCheck]]
  ) =
    (mockTaxCheckService
      .getAllTaxCheckCodesByExtractedStatus(_: Boolean, _: Int, _: Int, _: String)(_: HeaderCarrier))
      .expects(isExtracted, skip, limit, sortBy, *)
      .returning(EitherT.fromEither[Future](result))

  def mockWithLock(lockId: String, lockObtained: Boolean) =
    (mockMongoLockService
      .withLock(_: String, _: Future[Either[models.Error, List[HECTaxCheck]]])(_: HECTaxCheckExtractionContext))
      .expects(lockId, *, *)
      .onCall { test =>
        if (lockObtained)
          test
            .productElement(1)
            .asInstanceOf[() => Future[Either[models.Error, List[HECTaxCheck]]]]()
            .map(Some(_))
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

  def mockStoreFile(fileContent: String, fileName: String, dirName: String)(
    result: Either[models.Error, ObjectSummaryWithMd5]
  ) =
    (mockFileStoreService
      .storeFile(_: String, _: String, _: String)(_: HeaderCarrier, _: HECTaxCheckExtractionContext))
      .expects(fileContent, fileName, dirName, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockGenerateUUID(result: UUID) = (() => mockUUIDGenerator.generateUUID).expects().returning(result)

  def mockFileNotify(fileNotifyRequest: SDESFileNotifyRequest)(result: Either[models.Error, Unit]) = (mockSDESService
    .fileNotify(_: SDESFileNotifyRequest)(_: HeaderCarrier))
    .expects(fileNotifyRequest, *)
    .returning(EitherT.fromEither[Future](result))

  implicit val hecTaxCheckExtractionContext: HECTaxCheckExtractionContext = new HECTaxCheckExtractionContext(system)

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      """
        | hec-file-extraction-details {
        |   force-lock-release-after = 5 minutes
        |   maximum-rows-per-file = 2
        |   file-notification-api {
        |       location = "sdes-stub"
        |       information-type = "ssa"
        |       recipient-or-sender = "hec"
        |       file-location-base-url = "http://localhost:8464"
        |   }
        |}
        |""".stripMargin
    )
  )

  val hecTaxCheckExtractionService = new HecTaxCheckExtractionServiceImpl(
    mockTaxCheckService,
    mockMongoLockService,
    mockFileCreationService,
    mockFileStoreService,
    mockSDESService,
    mockUUIDGenerator,
    config
  )

  val lockId = "hecTaxChecks"

  "HecTaxCheckExtractionServiceSpec" must {

    val taxCheckData  = CompanyHECTaxCheckData(
      CompanyApplicantDetails(GGCredId("").some, CRN(""), CompanyHouseName("Test Tech Ltd")),
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
      Some(Language.English),
      None,
      None
    )
    val taxCheckCode1 = HECTaxCheckCode("code1")
    val taxCheckCode2 = HECTaxCheckCode("code12")
    val taxCheckCode3 = HECTaxCheckCode("code13")
    val taxCheckCode4 = HECTaxCheckCode("code14")
    val taxCheckCode5 = HECTaxCheckCode("code15")

    val taxCheck1 =
      hecTaxCheck.HECTaxCheck(taxCheckData, taxCheckCode1, TimeUtils.today(), TimeUtils.now(), false, None, None)
    val taxCheck2 = taxCheck1.copy(
      taxCheckCode = taxCheckCode2,
      taxCheckData = taxCheckData.copy(filterFromFileTransfer = Some(false))
    )
    val taxCheck3 = taxCheck1.copy(taxCheckCode = taxCheckCode3, isExtracted = false)
    val taxCheck4 = taxCheck1.copy(
      taxCheckCode = taxCheckCode4,
      taxCheckData = taxCheckData.copy(filterFromFileTransfer = Some(true))
    )
    val taxCheck5 = taxCheck4.copy(taxCheckCode = taxCheckCode5)

    val sdesDirectory = "sdes"
    val uuid          = UUID.randomUUID()

    val updatedTaxCheck1 = taxCheck1.copy(fileCorrelationId = uuid.some)
    val updatedTaxCheck2 = taxCheck2.copy(fileCorrelationId = uuid.some)
    val updatedTaxCheck3 = taxCheck3.copy(fileCorrelationId = uuid.some)

    // hash values below taken from actual unmodified value sent to SDES and value calculated by SDES in test
    val (md5Hash, expectedFileChecksum) =
      Md5Hash("a6WaSjh/V3z7xWS9JTTztQ==") -> FileChecksum("md5", "6ba59a4a387f577cfbc564bd2534f3b5")

    def createObjectSummary(dirName: String, fileName: String) =
      ObjectSummaryWithMd5(
        File(Directory(dirName), fileName),
        2000,
        md5Hash,
        Instant.ofEpochSecond(1636375166)
      )

    def createFileNotifyRequest(fileName: String, location: String) =
      SDESFileNotifyRequest(
        "ssa",
        FileMetaData(
          "hec",
          fileName,
          s"http://localhost:8464/object-store/object/$location/$fileName",
          expectedFileChecksum,
          2000,
          List()
        ),
        FileAudit(uuid.toString)
      )

    "return None" when {

      "no lock is obtained on mongo db" in {
        inSequence {
          mockWithLock(lockId, lockObtained = false)
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()

        await(result) shouldBe None
      }

    }

    "return an error" when {

      "there is error in fetching data from mongo" in {
        inSequence {
          mockWithLock(lockId, lockObtained = true)
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/licence-type")(
            Right(createObjectSummary(s"$sdesDirectory/licence-type", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-type"))(Right(()))
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceTimeTrading, "0001", "HEC_LICENCE_TIME_TRADING", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-time-trading"
          )(
            Right(createObjectSummary(s"$sdesDirectory/licence-time-trading", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-time-trading"))(Right(()))
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceValidityPeriod, "0001", "HEC_LICENCE_VALIDITY_PERIOD", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-validity-period"
          )(
            Right(createObjectSummary(s"$sdesDirectory/licence-validity-period", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-validity-period"))(Right(()))
          mockGenerateUUID(uuid)
          mockCreateFileContent(CorrectiveAction, "0001", "HEC_CORRECTIVE_ACTION", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/corrective-action"
          )(
            Right(createObjectSummary(s"$sdesDirectory/corrective-action", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/corrective-action"))(Right(()))
          mockGetAlltaxCheckByExtractedStatus(false, 0, 2, "_id")(Left(models.Error("a")))
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Left(models.Error("a")))
      }

      "There is an error in file creation" in {
        inSequence {
          mockWithLock(lockId, lockObtained = true)
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(Left(models.Error("err")))
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Left(models.Error("err")))
      }

      "There is an error in file storage" in {
        inSequence {
          mockWithLock(lockId, lockObtained = true)
          mockGenerateUUID(uuid)
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
          mockWithLock(lockId, lockObtained = true)
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/licence-type")(
            Right(createObjectSummary(s"$sdesDirectory/licence-type", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-type"))(Right(()))
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceTimeTrading, "0001", "HEC_LICENCE_TIME_TRADING", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-time-trading"
          )(
            Right(createObjectSummary(s"$sdesDirectory/licence-time-trading", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-time-trading"))(Right(()))
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceValidityPeriod, "0001", "HEC_LICENCE_VALIDITY_PERIOD", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-validity-period"
          )(
            Right(createObjectSummary(s"$sdesDirectory/licence-validity-period", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-validity-period"))(Right(()))
          mockGenerateUUID(uuid)
          mockCreateFileContent(CorrectiveAction, "0001", "HEC_CORRECTIVE_ACTION", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/corrective-action"
          )(
            Right(createObjectSummary(s"$sdesDirectory/corrective-action", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/corrective-action"))(Right(()))
          mockGetAlltaxCheckByExtractedStatus(false, 0, 2, "_id")(Right(List(taxCheck1, taxCheck2)))
          mockGetAlltaxCheckByExtractedStatus(false, 2, 2, "_id")(Right(List()))
          mockGenerateUUID(uuid)
          mockCreateFileContent(HECTaxCheckFileBodyList(List(taxCheck1, taxCheck2)), "0001", "HEC_APPLICATION", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/tax-checks"
          )(
            Right(createObjectSummary(s"$sdesDirectory/tax-checks", "file1.dat"))
          )
          mockUpdateAllHecTaxCheck(List(updatedTaxCheck1, updatedTaxCheck2))(Left(models.Error("err")))
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Left(models.Error("err")))
      }

      "There is an error in file notify" in {
        inSequence {
          mockWithLock(lockId, lockObtained = true)
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/licence-type")(
            Right(createObjectSummary(s"$sdesDirectory/licence-type", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-type"))(Left(Error("err")))
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Left(models.Error("err")))
      }

    }

    "lock and process the hec data" when {

      "all fetch , update , file creation , file storage  and file notify passed without error" in {
        inSequence {
          mockWithLock(lockId, lockObtained = true)
          // licence type enum file
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/licence-type")(
            Right(createObjectSummary(s"$sdesDirectory/licence-type", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-type"))(Right(()))

          // licence time trading enum file
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceTimeTrading, "0001", "HEC_LICENCE_TIME_TRADING", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-time-trading"
          )(
            Right(createObjectSummary(s"$sdesDirectory/licence-time-trading", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-time-trading"))(Right(()))

          // licence validity period enum file
          mockGenerateUUID(uuid)
          mockCreateFileContent(LicenceValidityPeriod, "0001", "HEC_LICENCE_VALIDITY_PERIOD", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/licence-validity-period"
          )(
            Right(createObjectSummary(s"$sdesDirectory/licence-validity-period", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/licence-validity-period"))(Right(()))

          // corrective action enum file
          mockGenerateUUID(uuid)
          mockCreateFileContent(CorrectiveAction, "0001", "HEC_CORRECTIVE_ACTION", true)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile(
            "00|file1.dat|HEC|SSA|20210909|154556|000001|001",
            "file1.dat",
            s"$sdesDirectory/corrective-action"
          )(
            Right(createObjectSummary(s"$sdesDirectory/corrective-action", "file1.dat"))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/corrective-action"))(Right(()))

          // tax check data loop 1
          mockGetAlltaxCheckByExtractedStatus(false, 0, 2, "_id")(Right(List(taxCheck1, taxCheck2)))
          // tax check 4 should be filtered from file feed
          mockGetAlltaxCheckByExtractedStatus(false, 2, 2, "_id")(Right(List(taxCheck3, taxCheck4)))
          mockGenerateUUID(uuid)
          mockCreateFileContent(HECTaxCheckFileBodyList(List(taxCheck1, taxCheck2)), "0001", "HEC_APPLICATION", false)(
            Right(("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat"))
          )
          mockStoreFile("00|file1.dat|HEC|SSA|20210909|154556|000001|001", "file1.dat", s"$sdesDirectory/tax-checks")(
            Right(createObjectSummary(s"$sdesDirectory/tax-checks", "file1.dat"))
          )
          mockUpdateAllHecTaxCheck(List(updatedTaxCheck1, updatedTaxCheck2))(
            Right(List(updatedTaxCheck1, updatedTaxCheck2))
          )
          mockFileNotify(createFileNotifyRequest("file1.dat", s"$sdesDirectory/tax-checks"))(Right(()))

          // tax check data loop 2 - tax check 5 should be filtered from file feed
          mockGetAlltaxCheckByExtractedStatus(false, 4, 2, "_id")(Right(List(taxCheck5)))
          mockGenerateUUID(uuid)
          mockCreateFileContent(HECTaxCheckFileBodyList(List(taxCheck3)), "0002", "HEC_APPLICATION", true)(
            Right(("00|file2.dat|HEC|SSA|20210909|154556|000001|001", "file2.dat"))
          )
          mockStoreFile("00|file2.dat|HEC|SSA|20210909|154556|000001|001", "file2.dat", s"$sdesDirectory/tax-checks")(
            Right(createObjectSummary(s"$sdesDirectory/tax-checks", "file2.dat"))
          )
          mockUpdateAllHecTaxCheck(List(updatedTaxCheck3))(Right(List(updatedTaxCheck3)))
          mockFileNotify(createFileNotifyRequest("file2.dat", s"$sdesDirectory/tax-checks"))(Right(()))
        }
        val result =
          hecTaxCheckExtractionService.lockAndProcessHecData()
        await(result) shouldBe Some(Right(()))

      }

    }
  }

}
