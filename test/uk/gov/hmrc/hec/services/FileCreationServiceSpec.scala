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

import cats.implicits.catsSyntaxOptionId
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.hec.models.hecTaxCheck.ApplicantDetails.{CompanyApplicantDetails, IndividualApplicantDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckSource.Digital
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxDetails.{CompanyTaxDetails, IndividualTaxDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTStatus, CTStatusResponse, CompanyHouseName}
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.{DateOfBirth, Name, SAStatus, SAStatusResponse}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.LicenceType._
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.hecTaxCheck._
import uk.gov.hmrc.hec.models.hecTaxCheck.company.CTAccountingPeriod.{CTAccountingPeriodDigital, CTAccountingPeriodStride}
import uk.gov.hmrc.hec.models.ids._
import uk.gov.hmrc.hec.models.{EmailAddress, Error, Language, hecTaxCheck}
import uk.gov.hmrc.hec.util.TimeProvider

import java.time.{LocalDate, LocalTime, ZoneId, ZonedDateTime}

class FileCreationServiceSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockTimeProvider = mock[TimeProvider]

  val fileCreationService = new FileCreationServiceImpl(mockTimeProvider)

  def mockDateProviderToday(d: LocalDate)               = (() => mockTimeProvider.currentDate).expects().returning(d)
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
        // In future if content grows we can reduce the test to check only for few lines or header and trailer.
        // As of now content is less, so no harm in testing i guess
        // same applies for other test
        val expected                                = s"""|00|HEC_SSA_0001_20211010_HEC_LICENCE_TYPE.dat|HEC|SSA|20211010|113605|000001|001
             |01|00|Driver of taxis and private hires
             |01|01|Operator of private hire vehicles
             |01|02|Scrap metal mobile collector
             |01|03|Scrap metal dealer site
             |01|04|Booking office
             |99|HEC_SSA_0001_20211010_HEC_LICENCE_TYPE.dat|7|Y
             |""".stripMargin

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
                           |99|HEC_SSA_0001_20211010_HEC_LICENCE_TIME_TRADING.dat|6|Y
                           |""".stripMargin

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
                           |99|HEC_SSA_0001_20211010_HEC_LICENCE_VALIDITY_PERIOD.dat|7|Y
                           |""".stripMargin

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
                                                          |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y
                                                          |""".stripMargin

        result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))

      }

      "content type is list of HecTaxChek" when {
        val partialFileName = "HEC"

        val zonedDateTime = ZonedDateTime.of(2021, 9, 9, 10, 9, 0, 0, ZoneId.of("Europe/London"))

        "there are records retrieved from database" when {

          "journey is for individual" when {

            val individualApplicantDetails =
              IndividualApplicantDetails(
                Some(GGCredId("AB123")),
                Name("Karen", "mcFie"),
                DateOfBirth(LocalDate.of(1922, 12, 1))
              )

            def createTaxDetails(
              taxSituation: TaxSituation,
              saStatusResponse: Option[SAStatusResponse],
              correctiveAction: Option[CorrectiveAction],
              saIncomeDeclared: Option[YesNoAnswer]
            ) =
              IndividualTaxDetails(
                NINO("AB123456C"),
                Some(SAUTR("1234567")),
                taxSituation,
                saIncomeDeclared,
                saStatusResponse,
                TaxYear(2021),
                correctiveAction
              )

            def createIndividualHecTaxCheck(
              licenceType: LicenceType,
              licenceTimeTrading: LicenceTimeTrading,
              licenceValidityPeriod: LicenceValidityPeriod,
              taxSituation: TaxSituation,
              saStatusResponse: Option[SAStatusResponse],
              correctiveAction: Option[CorrectiveAction],
              emailAddress: Option[EmailAddress],
              saIncomeDeclared: Option[YesNoAnswer]
            ) =
              HECTaxCheck(
                IndividualHECTaxCheckData(
                  individualApplicantDetails,
                  LicenceDetails(licenceType, licenceTimeTrading, licenceValidityPeriod),
                  createTaxDetails(taxSituation, saStatusResponse, correctiveAction, saIncomeDeclared),
                  zonedDateTime,
                  Digital,
                  Some(Language.Welsh),
                  None,
                  None
                ),
                HECTaxCheckCode("XNFFGBDD6"),
                LocalDate.of(9999, 2, 10),
                zonedDateTime,
                false,
                None,
                emailAddress
              )

            "as an Employee" in {
              val hecTaxCheckList = List(
                createIndividualHecTaxCheck(
                  DriverOfTaxisAndPrivateHires,
                  LicenceTimeTrading.FourToEightYears,
                  LicenceValidityPeriod.UpToFiveYears,
                  TaxSituation.PAYE,
                  Some(SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.ReturnFound)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  Some(EmailAddress("email")),
                  Some(YesNoAnswer.Yes)
                ),
                createIndividualHecTaxCheck(
                  DriverOfTaxisAndPrivateHires,
                  LicenceTimeTrading.TwoToFourYears,
                  LicenceValidityPeriod.UpToFiveYears,
                  TaxSituation.PAYE,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoticeToFileIssued)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  None,
                  Some(YesNoAnswer.No)
                ),
                createIndividualHecTaxCheck(
                  BookingOffice,
                  LicenceTimeTrading.TwoToFourYears,
                  LicenceValidityPeriod.UpToFiveYears,
                  TaxSituation.PAYE,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoReturnFound)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  None,
                  None
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||00|04|02|I||Y|N|2022|||||Y||Y|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|email
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||00|04|01|I||Y|N|2022|||||N|Y|N|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||04|04|01|I||Y|N|2022|||||N|N||00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
              |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y
              |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
            }

            "Registered for SA" in {
              val hecTaxCheckList = List(
                createIndividualHecTaxCheck(
                  OperatorOfPrivateHireVehicles,
                  LicenceTimeTrading.FourToEightYears,
                  LicenceValidityPeriod.UpToOneYear,
                  TaxSituation.SA,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.ReturnFound)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  Some(EmailAddress("email")),
                  None
                ),
                createIndividualHecTaxCheck(
                  ScrapMetalMobileCollector,
                  LicenceTimeTrading.TwoToFourYears,
                  LicenceValidityPeriod.UpToTwoYears,
                  TaxSituation.SA,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoticeToFileIssued)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  None,
                  Some(YesNoAnswer.No)
                ),
                createIndividualHecTaxCheck(
                  ScrapMetalDealerSite,
                  LicenceTimeTrading.TwoToFourYears,
                  LicenceValidityPeriod.UpToThreeYears,
                  TaxSituation.SA,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoReturnFound)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  None,
                  Some(YesNoAnswer.Yes)
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val partialFileName                         = "HEC"
              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||01|00|02|I||N|Y|2022|||||Y|||00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|email
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||02|01|01|I||N|Y|2022|||||N|Y|N|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||03|02|01|I||N|Y|2022|||||N|N|Y|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
              |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y
              |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
            }

            "Registered for SA and an employee" in {
              val hecTaxCheckList = List(
                createIndividualHecTaxCheck(
                  OperatorOfPrivateHireVehicles,
                  LicenceTimeTrading.FourToEightYears,
                  LicenceValidityPeriod.UpToOneYear,
                  TaxSituation.SAPAYE,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.ReturnFound)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  Some(EmailAddress("email")),
                  Some(YesNoAnswer.No)
                ),
                createIndividualHecTaxCheck(
                  ScrapMetalMobileCollector,
                  LicenceTimeTrading.TwoToFourYears,
                  LicenceValidityPeriod.UpToTwoYears,
                  TaxSituation.SAPAYE,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoticeToFileIssued)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  None,
                  None
                ),
                createIndividualHecTaxCheck(
                  ScrapMetalDealerSite,
                  LicenceTimeTrading.TwoToFourYears,
                  LicenceValidityPeriod.UpToThreeYears,
                  TaxSituation.SAPAYE,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoReturnFound)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  None,
                  Some(YesNoAnswer.Yes)
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val partialFileName                         = "HEC"
              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||01|00|02|I||Y|Y|2022|||||Y||N|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|email
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||02|01|01|I||Y|Y|2022|||||N|Y||00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||03|02|01|I||Y|Y|2022|||||N|N|Y|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
              |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y
              |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
            }

            "Individual - who don't have to pay tax" in {
              val hecTaxCheckList = List(
                createIndividualHecTaxCheck(
                  OperatorOfPrivateHireVehicles,
                  LicenceTimeTrading.FourToEightYears,
                  LicenceValidityPeriod.UpToOneYear,
                  TaxSituation.NotChargeable,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.ReturnFound)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  None,
                  Some(YesNoAnswer.Yes)
                ),
                createIndividualHecTaxCheck(
                  ScrapMetalMobileCollector,
                  LicenceTimeTrading.TwoToFourYears,
                  LicenceValidityPeriod.UpToTwoYears,
                  TaxSituation.NotChargeable,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoticeToFileIssued)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  None,
                  None
                ),
                createIndividualHecTaxCheck(
                  ScrapMetalDealerSite,
                  LicenceTimeTrading.TwoToFourYears,
                  LicenceValidityPeriod.UpToThreeYears,
                  TaxSituation.NotChargeable,
                  Some(individual.SAStatusResponse(SAUTR("1234567"), TaxYear(2021), SAStatus.NoReturnFound)),
                  Some(CorrectiveAction.RegisterNewSAAccount),
                  None,
                  Some(YesNoAnswer.No)
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val partialFileName                         = "HEC"
              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||01|00|02|I|Y|||2022|||||Y||Y|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||02|01|01|I|Y|||2022|||||N|Y||00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
              |01|AB123|AB123456C|Karen|mcFie|19221201|1234567||||03|02|01|I|Y|||2022|||||N|N|N|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
              |99|HEC_SSA_0001_20211010_$partialFileName.dat|5|Y
                                 |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
            }

          }

          "journey is for company" when {

            val startDate = LocalDate.of(2020, 10, 9)
            val endDate   = LocalDate.of(2021, 10, 9)

            val companyDetails =
              CompanyApplicantDetails(GGCredId("AB123").some, CRN("1123456"), CompanyHouseName("Test Tech Ltd"))

            def getCTStatusResponse(status: CTStatus) = CTStatusResponse(
              CTUTR("1111111111"),
              startDate,
              endDate,
              Some(CTAccountingPeriodDigital(startDate, endDate, status))
            )

            def createTaxDetails(
              ctStatusResponse: CTStatusResponse,
              ctIncomeDeclared: Option[YesNoAnswer],
              recentlyStaredTrading: Option[YesNoAnswer],
              chargeableForCT: Option[YesNoAnswer],
              correctiveAction: Option[CorrectiveAction]
            ) = CompanyTaxDetails(
              CTUTR("1111111111"),
              Some(CTUTR("1111111111")),
              ctIncomeDeclared,
              ctStatusResponse,
              recentlyStaredTrading,
              chargeableForCT,
              correctiveAction
            )

            def createCompanyHecTaxCheck(
              licenceType: LicenceType,
              licenceTimeTrading: LicenceTimeTrading,
              licenceValidityPeriod: LicenceValidityPeriod,
              ctStatusResponse: CTStatusResponse,
              ctIncomeDeclared: Option[YesNoAnswer],
              recentlyStaredTrading: Option[YesNoAnswer],
              chargeableForCT: Option[YesNoAnswer],
              correctiveAction: Option[CorrectiveAction],
              emailAddress: Option[EmailAddress]
            ) =
              hecTaxCheck.HECTaxCheck(
                CompanyHECTaxCheckData(
                  companyDetails,
                  LicenceDetails(licenceType, licenceTimeTrading, licenceValidityPeriod),
                  createTaxDetails(
                    ctStatusResponse,
                    ctIncomeDeclared,
                    recentlyStaredTrading,
                    chargeableForCT,
                    correctiveAction
                  ),
                  zonedDateTime,
                  Digital,
                  Some(Language.English),
                  None,
                  Some(false)
                ),
                HECTaxCheckCode("XNFFGBDD6"),
                LocalDate.of(9999, 2, 10),
                zonedDateTime,
                false,
                None,
                emailAddress
              )

            "ct status api response is return Found" in {
              val hecTaxCheckList = List(
                createCompanyHecTaxCheck(
                  licenceType = OperatorOfPrivateHireVehicles,
                  licenceTimeTrading = LicenceTimeTrading.TwoToFourYears,
                  licenceValidityPeriod = LicenceValidityPeriod.UpToOneYear,
                  ctStatusResponse = getCTStatusResponse(CTStatus.ReturnFound),
                  ctIncomeDeclared = Some(YesNoAnswer.Yes),
                  recentlyStaredTrading = None,
                  chargeableForCT = Some(YesNoAnswer.Yes),
                  correctiveAction = Some(CorrectiveAction.RegisterNewSAAccount),
                  emailAddress = Some(EmailAddress("email"))
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                 |01|AB123||||||1111111111|1123456|Test Tech Ltd|01|00|01|C|N||||Y|20201009|20211009||Y||Y|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|email
                                 |99|HEC_SSA_0001_20211010_$partialFileName.dat|3|Y
                                 |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
            }

            "ct status api response is return NoticeToFile" in {
              val hecTaxCheckList = List(
                createCompanyHecTaxCheck(
                  licenceType = OperatorOfPrivateHireVehicles,
                  licenceTimeTrading = LicenceTimeTrading.TwoToFourYears,
                  licenceValidityPeriod = LicenceValidityPeriod.UpToOneYear,
                  ctStatusResponse = getCTStatusResponse(CTStatus.NoticeToFileIssued),
                  ctIncomeDeclared = Some(YesNoAnswer.Yes),
                  recentlyStaredTrading = None,
                  chargeableForCT = Some(YesNoAnswer.Yes),
                  correctiveAction = Some(CorrectiveAction.RegisterNewSAAccount),
                  None
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                 |01|AB123||||||1111111111|1123456|Test Tech Ltd|01|00|01|C|N||||Y|20201009|20211009||N|Y|Y|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
                                 |99|HEC_SSA_0001_20211010_$partialFileName.dat|3|Y
                                 |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
            }

            "ct status api response is return No return Found" in {
              val hecTaxCheckList = List(
                createCompanyHecTaxCheck(
                  licenceType = OperatorOfPrivateHireVehicles,
                  licenceTimeTrading = LicenceTimeTrading.TwoToFourYears,
                  licenceValidityPeriod = LicenceValidityPeriod.UpToOneYear,
                  ctStatusResponse = getCTStatusResponse(CTStatus.NoReturnFound),
                  ctIncomeDeclared = None,
                  recentlyStaredTrading = None,
                  chargeableForCT = None,
                  correctiveAction = Some(CorrectiveAction.RegisterNewSAAccount),
                  emailAddress = None
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                 |01|AB123||||||1111111111|1123456|Test Tech Ltd|01|00|01|C|||||Y|20201009|20211009||N|N||00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
                                 |99|HEC_SSA_0001_20211010_$partialFileName.dat|3|Y
                                 |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
            }

            "(Digital journey)- not chargeable but has CTStatus , no Ct status status should be recorded in file but accounting period will be Y" in {

              val hecTaxCheckList = List(
                createCompanyHecTaxCheck(
                  licenceType = OperatorOfPrivateHireVehicles,
                  licenceTimeTrading = LicenceTimeTrading.TwoToFourYears,
                  licenceValidityPeriod = LicenceValidityPeriod.UpToOneYear,
                  ctStatusResponse = getCTStatusResponse(CTStatus.NoticeToFileIssued),
                  ctIncomeDeclared = Some(YesNoAnswer.Yes),
                  recentlyStaredTrading = None,
                  chargeableForCT = Some(YesNoAnswer.No),
                  correctiveAction = Some(CorrectiveAction.RegisterNewSAAccount),
                  emailAddress = Some(EmailAddress("email"))
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                 |01|AB123||||||1111111111|1123456|Test Tech Ltd|01|00|01|C|Y||||Y|20201009|20211009||||Y|00|Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|email
                                 |99|HEC_SSA_0001_20211010_$partialFileName.dat|3|Y
                                 |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))

            }

            "(Stride Journey )-not chargeable but has accounting period end date " in {
              val hecTaxCheckList = List(
                createCompanyHecTaxCheck(
                  licenceType = OperatorOfPrivateHireVehicles,
                  licenceTimeTrading = LicenceTimeTrading.TwoToFourYears,
                  licenceValidityPeriod = LicenceValidityPeriod.UpToOneYear,
                  ctStatusResponse = CTStatusResponse(
                    CTUTR("1111111111"),
                    startDate,
                    endDate,
                    CTAccountingPeriodStride(LocalDate.of(2021, 10, 8), None).some
                  ),
                  ctIncomeDeclared = None,
                  recentlyStaredTrading = None,
                  chargeableForCT = YesNoAnswer.No.some,
                  correctiveAction = None,
                  emailAddress = None
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                 |01|AB123||||||1111111111|1123456|Test Tech Ltd|01|00|01|C|Y||||Y||20211008||||||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
                                 |99|HEC_SSA_0001_20211010_$partialFileName.dat|3|Y
                                 |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
            }

            "no accounting period found" in {
              val hecTaxCheckList = List(
                createCompanyHecTaxCheck(
                  licenceType = OperatorOfPrivateHireVehicles,
                  licenceTimeTrading = LicenceTimeTrading.TwoToFourYears,
                  licenceValidityPeriod = LicenceValidityPeriod.UpToOneYear,
                  ctStatusResponse = CTStatusResponse(CTUTR("1111111111"), startDate, endDate, None),
                  ctIncomeDeclared = None,
                  recentlyStaredTrading = Some(YesNoAnswer.Yes),
                  chargeableForCT = None,
                  correctiveAction = None,
                  emailAddress = None
                )
              )

              inSequence {
                mockDateProviderToday(LocalDate.of(2021, 10, 10))
                mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
              }

              val result: Either[Error, (String, String)] =
                fileCreationService.createFileContent(
                  HECTaxCheckFileBodyList(hecTaxCheckList),
                  "0001",
                  partialFileName,
                  true
                )

              val expected = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                 |01|AB123||||||1111111111|1123456|Test Tech Ltd|01|00|01|C|||||N|||Y|||||Y|20210909090900|20210909090900|XNFFGBDD6|99990210|Y|
                                 |99|HEC_SSA_0001_20211010_$partialFileName.dat|3|Y
                                 |""".stripMargin

              result shouldBe Right((expected, s"HEC_SSA_0001_20211010_$partialFileName.dat"))
            }

          }
        }

        "there are no records retrieved from database" in {
          val hecTaxCheckList = List()

          inSequence {
            mockDateProviderToday(LocalDate.of(2021, 10, 10))
            mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
          }

          val result: Either[Error, (String, String)] =
            fileCreationService.createFileContent(
              HECTaxCheckFileBodyList(hecTaxCheckList),
              "0001",
              partialFileName,
              true
            )
          val expected                                = s"""|00|HEC_SSA_0001_20211010_$partialFileName.dat|HEC|SSA|20211010|113605|000001|001
                                                            |99|HEC_SSA_0001_20211010_$partialFileName.dat|2|Y
                                                            |""".stripMargin

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
