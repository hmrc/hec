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

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.hec.models.ApplicantDetails.IndividualApplicantDetails
import uk.gov.hmrc.hec.models.HECTaxCheckData.IndividualHECTaxCheckData
import uk.gov.hmrc.hec.models.HECTaxCheckSource.Digital
import uk.gov.hmrc.hec.models.TaxDetails.IndividualTaxDetails
import uk.gov.hmrc.hec.models.ids.{GGCredId, NINO, SAUTR}
import uk.gov.hmrc.hec.models.licence.LicenceType.{DriverOfTaxisAndPrivateHires, OperatorOfPrivateHireVehicles, ScrapMetalDealerSite, ScrapMetalMobileCollector}
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.{CorrectiveAction, DateOfBirth, Error, HECTaxCheck, HECTaxCheckCode, HECTaxCheckFileBodyList, Name, SAStatus, SAStatusResponse, TaxSituation, TaxYear, YesNoAnswer}
import uk.gov.hmrc.hec.util.TimeProvider

import java.time.{LocalDate, LocalTime, ZoneId, ZonedDateTime}

class FileCreationServiceSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockTimeProvider = mock[TimeProvider]

  val fileCreationService = new FileCreationServiceImpl(mockTimeProvider)

  def mockDateProviderToday(d: LocalDate)               = (mockTimeProvider.currentDate _).expects().returning(d)
  def mockTimeProviderNow(d: LocalTime, zoneId: ZoneId) =
    (mockTimeProvider.currentTime(_: ZoneId)).expects(zoneId).returning(d)

  val zoneId = ZoneId.of("GMT")

  "FileCreationServiceSpec" must {

    "create file content" when {

      "content Type is licence Type" in {

        inSequence {
          mockDateProviderToday(LocalDate.of(2021, 10, 10))
          mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
        }
        val result: Either[Error, (String, String)] =
          fileCreationService.createFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE", true)
        //In future if content grows we can reduce the test to check only for few lines or header and trailer.
        //As of now content is less, so no harm in testing i guess
        //same applies for other test
        val expected                                = s"""|00|HEC_SSA_0001_20211010_HEC_LICENCE_TYPE.dat|HEC|SSA|20211010|113605|000001|001
             |01|00|Driver of taxis and private hires
             |01|01|Operator of private hire vehicles
             |01|02|Scrap metal mobile collector
             |01|03|Scrap metal dealer site
             |99|HEC_SSA_0001_20211010_HEC_LICENCE_TYPE.dat|6|Y""".stripMargin

        result shouldBe (Right((expected, "HEC_SSA_0001_20211010_HEC_LICENCE_TYPE.dat")))

      }

      "content Type is licence Time Trading" in {

        inSequence {
          mockDateProviderToday(LocalDate.of(2021, 10, 10))
          mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
        }
        val result: Either[Error, (String, String)] =
          fileCreationService.createFileContent(LicenceTimeTrading, "0001", "HEC_LICENCE_TIME_TRADING", true)
        val expected                                = s"""|00|HEC_SSA_0001_20211010_HEC_LICENCE_TIME_TRADING.dat|HEC|SSA|20211010|113605|000001|001
                           |01|00|0 to 2 years
                           |01|01|2 to 4 years
                           |01|02|4 to 8 years
                           |01|03|More than 8 years
                           |99|HEC_SSA_0001_20211010_HEC_LICENCE_TIME_TRADING.dat|6|Y""".stripMargin

        result shouldBe Right((expected, "HEC_SSA_0001_20211010_HEC_LICENCE_TIME_TRADING.dat"))

      }

      "content Type is licence Validity period" in {

        inSequence {
          mockDateProviderToday(LocalDate.of(2021, 10, 10))
          mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
        }
        val result: Either[Error, (String, String)] =
          fileCreationService.createFileContent(LicenceValidityPeriod, "0001", "HEC_LICENCE_VALIDITY_PERIOD", true)
        val expected                                = s"""|00|HEC_SSA_0001_20211010_HEC_LICENCE_VALIDITY_PERIOD.dat|HEC|SSA|20211010|113605|000001|001
                           |01|00|Up to 1 year
                           |01|01|Up to 2 years
                           |01|02|Up to 3 years
                           |01|03|Up to 4 years
                           |01|04|Up to 5 years
                           |99|HEC_SSA_0001_20211010_HEC_LICENCE_VALIDITY_PERIOD.dat|7|Y""".stripMargin

        result shouldBe Right((expected, "HEC_SSA_0001_20211010_HEC_LICENCE_VALIDITY_PERIOD.dat"))

      }

      "content Type is Corrective Action" in {

        val partialFileName = "HEC_CORRECTIVE_ACTION"

        inSequence {
          mockDateProviderToday(LocalDate.of(2021, 10, 10))
          mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
        }
        val result: Either[Error, (String, String)] =
          fileCreationService.createFileContent(CorrectiveAction, "0001", partialFileName, true)
        val expected                                = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                                          |01|00|Register new SA account
                                                          |01|01|Dormant account reactivated
                                                          |01|02|Other corrective action
                                                          |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y""".stripMargin

        result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))

      }

      "content type is list of HecTaxChek" when {

        val zonedDateTime              = ZonedDateTime.of(2021, 9, 9, 10, 9, 0, 0, ZoneId.of("Europe/London"))
        val individualApplicantDetails =
          IndividualApplicantDetails(GGCredId("AB123"), Name("Karen", "mcFie"), DateOfBirth(LocalDate.of(1922, 12, 1)))

        def createTaxDetails(taxSituation: TaxSituation, saStatusResponse: Option[SAStatusResponse]) =
          IndividualTaxDetails(
            NINO("AB123456C"),
            Some(SAUTR("1234567")),
            taxSituation,
            Some(YesNoAnswer.Yes),
            saStatusResponse
          )

        def createHecTaxCheck(
          licenceType: LicenceType,
          licenceTimeTrading: LicenceTimeTrading,
          licenceValidityPeriod: LicenceValidityPeriod,
          taxSituation: TaxSituation,
          saStatusResponse: Option[SAStatusResponse],
          correctiveAction: Option[CorrectiveAction]
        ) =
          HECTaxCheck(
            IndividualHECTaxCheckData(
              individualApplicantDetails,
              LicenceDetails(licenceType, licenceTimeTrading, licenceValidityPeriod),
              createTaxDetails(taxSituation, saStatusResponse),
              zonedDateTime,
              Digital
            ),
            HECTaxCheckCode("XNFFGBDD6"),
            LocalDate.of(9999, 2, 10),
            zonedDateTime,
            false,
            correctiveAction
          )

        "journey is for Individual as an Employee" in {
          val hecTaxCheckList = List(
            createHecTaxCheck(
              DriverOfTaxisAndPrivateHires,
              LicenceTimeTrading.FourToEightYears,
              LicenceValidityPeriod.UpToFiveYears,
              TaxSituation.PAYE,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.ReturnFound)),
              Some(CorrectiveAction.Register)
            ),
            createHecTaxCheck(
              DriverOfTaxisAndPrivateHires,
              LicenceTimeTrading.TwoToFourYears,
              LicenceValidityPeriod.UpToFiveYears,
              TaxSituation.PAYE,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoticeToFileIssued)),
              Some(CorrectiveAction.Register)
            ),
            createHecTaxCheck(
              DriverOfTaxisAndPrivateHires,
              LicenceTimeTrading.TwoToFourYears,
              LicenceValidityPeriod.UpToFiveYears,
              TaxSituation.PAYE,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoReturnFound)),
              Some(CorrectiveAction.Register)
            )
          )

          inSequence {
            mockDateProviderToday(LocalDate.of(2021, 10, 10))
            mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
          }

          val partialFileName                         = s"HEC"
          val result: Either[Error, (String, String)] =
            fileCreationService.createFileContent(
              HECTaxCheckFileBodyList(hecTaxCheckList),
              "0001",
              partialFileName,
              true
            )
          val expected                                = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||00|04|02|I||Y|N|2022|||||Y||Y||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||00|04|01|I||Y|N|2022|||||N|Y|N||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||00|04|01|I||Y|N|2022|||||N|N|N||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y""".stripMargin

          result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
        }

        "journey is for Individual - Registered for SA" in {
          val hecTaxCheckList = List(
            createHecTaxCheck(
              OperatorOfPrivateHireVehicles,
              LicenceTimeTrading.FourToEightYears,
              LicenceValidityPeriod.UpToOneYear,
              TaxSituation.SA,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.ReturnFound)),
              Some(CorrectiveAction.Register)
            ),
            createHecTaxCheck(
              ScrapMetalMobileCollector,
              LicenceTimeTrading.TwoToFourYears,
              LicenceValidityPeriod.UpToTwoYears,
              TaxSituation.SA,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoticeToFileIssued)),
              Some(CorrectiveAction.Register)
            ),
            createHecTaxCheck(
              ScrapMetalDealerSite,
              LicenceTimeTrading.TwoToFourYears,
              LicenceValidityPeriod.UpToThreeYears,
              TaxSituation.SA,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoReturnFound)),
              Some(CorrectiveAction.Register)
            )
          )

          inSequence {
            mockDateProviderToday(LocalDate.of(2021, 10, 10))
            mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
          }

          val partialFileName                         = s"HEC"
          val result: Either[Error, (String, String)] =
            fileCreationService.createFileContent(
              HECTaxCheckFileBodyList(hecTaxCheckList),
              "0001",
              partialFileName,
              true
            )
          val expected                                = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||01|00|02|I||N|Y|2022|||||Y||Y||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||02|01|01|I||N|Y|2022|||||N|Y|N||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||03|02|01|I||N|Y|2022|||||N|N|N||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y""".stripMargin

          result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
        }

        "journey is for Individual - Registered for SA and an employee" in {
          val hecTaxCheckList = List(
            createHecTaxCheck(
              OperatorOfPrivateHireVehicles,
              LicenceTimeTrading.FourToEightYears,
              LicenceValidityPeriod.UpToOneYear,
              TaxSituation.SAPAYE,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.ReturnFound)),
              Some(CorrectiveAction.Register)
            ),
            createHecTaxCheck(
              ScrapMetalMobileCollector,
              LicenceTimeTrading.TwoToFourYears,
              LicenceValidityPeriod.UpToTwoYears,
              TaxSituation.SAPAYE,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoticeToFileIssued)),
              Some(CorrectiveAction.Register)
            ),
            createHecTaxCheck(
              ScrapMetalDealerSite,
              LicenceTimeTrading.TwoToFourYears,
              LicenceValidityPeriod.UpToThreeYears,
              TaxSituation.SAPAYE,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoReturnFound)),
              Some(CorrectiveAction.Register)
            )
          )

          inSequence {
            mockDateProviderToday(LocalDate.of(2021, 10, 10))
            mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
          }

          val partialFileName                         = s"HEC"
          val result: Either[Error, (String, String)] =
            fileCreationService.createFileContent(
              HECTaxCheckFileBodyList(hecTaxCheckList),
              "0001",
              partialFileName,
              true
            )
          val expected                                = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||01|00|02|I||Y|Y|2022|||||Y||Y||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||02|01|01|I||Y|Y|2022|||||N|Y|N||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||03|02|01|I||Y|Y|2022|||||N|N|N||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y""".stripMargin

          result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
        }

        "journey is for Individual - who don't have to pay tax" in {
          val hecTaxCheckList = List(
            createHecTaxCheck(
              OperatorOfPrivateHireVehicles,
              LicenceTimeTrading.FourToEightYears,
              LicenceValidityPeriod.UpToOneYear,
              TaxSituation.NotChargeable,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.ReturnFound)),
              Some(CorrectiveAction.Register)
            ),
            createHecTaxCheck(
              ScrapMetalMobileCollector,
              LicenceTimeTrading.TwoToFourYears,
              LicenceValidityPeriod.UpToTwoYears,
              TaxSituation.NotChargeable,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoticeToFileIssued)),
              Some(CorrectiveAction.Register)
            ),
            createHecTaxCheck(
              ScrapMetalDealerSite,
              LicenceTimeTrading.TwoToFourYears,
              LicenceValidityPeriod.UpToThreeYears,
              TaxSituation.NotChargeable,
              Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoReturnFound)),
              Some(CorrectiveAction.Register)
            )
          )

          inSequence {
            mockDateProviderToday(LocalDate.of(2021, 10, 10))
            mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
          }

          val partialFileName                         = s"HEC"
          val result: Either[Error, (String, String)] =
            fileCreationService.createFileContent(
              HECTaxCheckFileBodyList(hecTaxCheckList),
              "0001",
              partialFileName,
              true
            )
          val expected                                = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||01|00|02|I|Y|||2022|||||Y||Y||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||02|01|01|I|Y|||2022|||||N|Y|N||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||03|02|01|I|Y|||2022|||||N|N|N||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y
                                                            |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y""".stripMargin

          result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
        }

      }

    }

    "return an error" when {

      "input type doesn't match the expected" in {
        val result: Either[Error, (String, String)] =
          fileCreationService.createFileContent("LicenceType", "0001", "LICENCE_TYPE", false)

        result.isLeft shouldBe true

      }
    }

  }

}
