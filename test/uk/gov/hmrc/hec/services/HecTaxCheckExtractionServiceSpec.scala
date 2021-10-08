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

///*
// * Copyright 2021 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.hec.services
//
//import akka.actor.ActorSystem
//import cats.data.EitherT
//import cats.instances.future._
//import com.typesafe.config.ConfigFactory
//import org.scalamock.scalatest.MockFactory
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AnyWordSpec
//import play.api.Configuration
//import play.api.test.Helpers._
//import uk.gov.hmrc.hec.models
//import uk.gov.hmrc.hec.models.HECTaxCheck
//import uk.gov.hmrc.hec.models.licence.LicenceType
//import uk.gov.hmrc.hec.services.scheduleService.{HECTaxCheckExtractionContext, HecTaxCheckExtractionServiceImpl}
//import uk.gov.hmrc.http.HeaderCarrier
//import uk.gov.hmrc.mongo.lock.MongoLockRepository
//
//import scala.concurrent.Future
//
//class HecTaxCheckExtractionServiceSpec extends AnyWordSpec with Matchers with MockFactory {
//  val mockTaxCheckService     = mock[TaxCheckService]
//  def mongoLockRepository     = mock[MongoLockRepository]
//  val mockFileCreationService = mock[FileCreationService]
//  val mockFileStoreService    = mock[FileStoreService]
//
//  def mockGetAlltaxCheckByExtractedStatus(isExtracted: Boolean)(result: Either[models.Error, List[HECTaxCheck]]) =
//    (mockTaxCheckService
//      .getAllTaxCheckCodesByExtractedStatus(_: Boolean)(_: HeaderCarrier))
//      .expects(isExtracted, *)
//      .returning(EitherT.fromEither[Future](result))
//
//  def mockUpdateAllHecTaxCheck(hecTaxCheckList: List[HECTaxCheck])(result: Either[models.Error, List[HECTaxCheck]]) =
//    (mockTaxCheckService
//      .updateAllHecTaxCheck(_: List[HECTaxCheck])(_: HeaderCarrier))
//      .expects(hecTaxCheckList, *)
//      .returning(EitherT.fromEither[Future](result))
//
//  def mockCreateFileContent[A](inputType: A, seqNum: String, partialFileName: String)(
//    result: Either[models.Error, (String, String)]
//  ) =
//    (mockFileCreationService
//      .createFileContent[A](_: A, _: String, _: String))
//      .expects(inputType, seqNum, partialFileName)
//      .returning(EitherT.fromEither[Future](result))
//
//  def mockStoreFile(fileContent: String, fileName: String, dirName: String)(result: Either[models.Error, Unit]) =
//    (mockFileStoreService
//      .storeFile(_: String, _: String, _: String)(_: HeaderCarrier, _: HECTaxCheckExtractionContext))
//      .expects(fileContent, fileName, dirName, *, *)
//      .returning(EitherT.fromEither[Future](result))
//
//  implicit val sys: ActorSystem                                           = ActorSystem("MyTest")
//  implicit val hecTaxCheckExtractionContext: HECTaxCheckExtractionContext = new HECTaxCheckExtractionContext(sys)
//
//  val config: Configuration = Configuration(
//    ConfigFactory.parseString(
//      """
//        | mongo-lock.force-lock-release-after = 30 minutes
//        |""".stripMargin
//    )
//  )
//
//  val hecTaxCheckExtractionService = new HecTaxCheckExtractionServiceImpl(
//    mockTaxCheckService,
//    mongoLockRepository,
//    mockFileCreationService,
//    mockFileStoreService,
//    config
//  )
//
//  "HecTaxCheckExtractionServiceSpec" must {
//
//    "return an error" when {
////      val taxCheckData  = CompanyHECTaxCheckData(
////        CompanyApplicantDetails(GGCredId(""), CRN("")),
////        LicenceDetails(
////          LicenceType.ScrapMetalDealerSite,
////          LicenceTimeTrading.EightYearsOrMore,
////          LicenceValidityPeriod.UpToOneYear
////        ),
////        CompanyTaxDetails(CTUTR(""))
////      )
////      val taxCheckCode1 = HECTaxCheckCode("code1")
////      val taxCheckCode2 = HECTaxCheckCode("code12")
////      val taxCheckCode3 = HECTaxCheckCode("code13")
////      val taxCheck1     = HECTaxCheck(taxCheckData, taxCheckCode1, TimeUtils.today(), TimeUtils.now(), false)
////      val taxCheck2     = taxCheck1.copy(taxCheckCode = taxCheckCode2)
////      val taxCheck3     = taxCheck1.copy(taxCheckCode = taxCheckCode3, isExtracted = true)
////
////      val hecTaxCheckList = List(taxCheck1, taxCheck2, taxCheck3)
//
//      "there is an error in fetching data from mongo db" in {
//        inSequence {
//          mockGetAlltaxCheckByExtractedStatus(false)(Left(models.Error("")))
//          mockUpdateAllHecTaxCheck(List())(Right(List()))
//          mockCreateFileContent(LicenceType, "00001", "LICENCE_TYPE")(Right(("001|file.date|00001", "file1.dat")))
//          mockStoreFile("001|file.date|00001", "file1.dat", "dir1")(Right(()))
//        }
//        val result = hecTaxCheckExtractionService.lockAndExtractJob()
//
//        await(result) shouldBe None
//      }
//
//    }
//  }
//
//}
