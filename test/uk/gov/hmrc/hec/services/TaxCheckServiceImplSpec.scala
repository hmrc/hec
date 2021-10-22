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

import cats.data.EitherT
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers._
import uk.gov.hmrc.hec.models.ApplicantDetails.{CompanyApplicantDetails, IndividualApplicantDetails}
import uk.gov.hmrc.hec.models.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.TaxDetails.{CompanyTaxDetails, IndividualTaxDetails}
import uk.gov.hmrc.hec.models.ids._
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.{CTAccountingPeriod, CTStatus, CTStatusResponse, CompanyHouseName, DateOfBirth, Error, HECTaxCheck, HECTaxCheckCode, HECTaxCheckData, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckMatchStatus, HECTaxCheckSource, Name, TaxCheckListItem, TaxSituation, YesNoAnswer}
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.util.{TimeProvider, TimeUtils}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class TaxCheckServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockTaxCheckCodeGeneratorService = mock[TaxCheckCodeGeneratorService]
  val mockTaxCheckStore                = mock[HECTaxCheckStore]
  val expiresAfter                     = 5.days
  val mockTimeProvider                 = mock[TimeProvider]

  val config = ConfigFactory.parseString(s"""{
      |hec-tax-check.expires-after = ${expiresAfter.toDays} days
      |}""".stripMargin)

  val service = new TaxCheckServiceImpl(mockTaxCheckCodeGeneratorService, mockTimeProvider, mockTaxCheckStore, config)

  def mockGenerateTaxCheckCode(taxCheckCode: HECTaxCheckCode) =
    (mockTaxCheckCodeGeneratorService.generateTaxCheckCode _).expects().returning(taxCheckCode)

  def mockStoreTaxCheck(taxCheck: HECTaxCheck)(result: Either[Error, Unit]) =
    (mockTaxCheckStore
      .store(_: HECTaxCheck)(_: HeaderCarrier))
      .expects(taxCheck, *)
      .returning(EitherT.fromEither(result))

  def mockGetTaxCheck(taxCheckCode: HECTaxCheckCode)(result: Either[Error, Option[HECTaxCheck]]) =
    (mockTaxCheckStore
      .get(_: HECTaxCheckCode)(_: HeaderCarrier))
      .expects(taxCheckCode, *)
      .returning(EitherT.fromEither(result))

  def mockGetTaxCheckCodes(ggCredId: GGCredId)(result: Either[Error, List[HECTaxCheck]]) =
    (mockTaxCheckStore
      .getTaxCheckCodes(_: GGCredId)(_: HeaderCarrier))
      .expects(ggCredId, *)
      .returning(EitherT.fromEither(result))

  def mockGetAllTaxCheckCodesByStatus(isExtracted: Boolean)(result: Either[Error, List[HECTaxCheck]]) =
    (mockTaxCheckStore
      .getAllTaxCheckCodesByExtractedStatus(_: Boolean)(_: HeaderCarrier))
      .expects(isExtracted, *)
      .returning(EitherT.fromEither(result))

  def mockTimeProviderNow(d: ZonedDateTime) = (mockTimeProvider.currentDateTime _).expects().returning(d)

  def mockTimeProviderToday(d: LocalDate) = (mockTimeProvider.currentDate _).expects().returning(d)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val taxCheckStartDateTime      = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("Europe/London"))
  private val now                = TimeUtils.now()
  private val today              = TimeUtils.today()

  "TaxCheckServiceImpl" when {

    "handling requests to save a tax check" must {

      val taxCheckData = IndividualHECTaxCheckData(
        IndividualApplicantDetails(GGCredId(""), Name("", ""), DateOfBirth(LocalDate.now())),
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
          None
        ),
        taxCheckStartDateTime,
        HECTaxCheckSource.Digital
      )

      val expectedExpiryDate = TimeUtils.today().plusDays(expiresAfter.toDays)
      val taxCheckCode       = HECTaxCheckCode("code")
      val taxCheck           = HECTaxCheck(taxCheckData, taxCheckCode, expectedExpiryDate, now, false, None)

      "return an error" when {

        "there is an error saving the tax check" in {
          inSequence {
            mockGenerateTaxCheckCode(taxCheckCode)
            mockTimeProviderToday(today)
            mockTimeProviderNow(now)
            mockStoreTaxCheck(taxCheck)(Left(Error("")))
          }

          val result = service.saveTaxCheck(taxCheckData).value
          await(result) shouldBe a[Left[_, _]]
        }

      }

      "return successfully" when {

        "the tax check has been saved" in {
          inSequence {
            mockGenerateTaxCheckCode(taxCheckCode)
            mockTimeProviderToday(today)
            mockTimeProviderNow(now)
            mockStoreTaxCheck(taxCheck)(Right(()))
          }

          val result = service.saveTaxCheck(taxCheckData).value
          await(result) shouldBe Right(taxCheck)
        }

      }

    }

    "handling requests to match a tax check" must {

      val taxCheckCode = HECTaxCheckCode("code")

      val (storedLicenceType, incorrectLicenceType) =
        LicenceType.ScrapMetalDealerSite -> LicenceType.DriverOfTaxisAndPrivateHires

      val (storedDateOfBirth, incorrectDateOfBirth) =
        DateOfBirth(LocalDate.now()) -> DateOfBirth(LocalDate.now().plusDays(1L))

      val (storedCRN, incorrectCRN) =
        CRN("crn") -> CRN("incorrect-crn")

      val storedLicenceDetails =
        LicenceDetails(
          storedLicenceType,
          LicenceTimeTrading.EightYearsOrMore,
          LicenceValidityPeriod.UpToOneYear
        )

      val storedIndividualTaxCheck =
        HECTaxCheck(
          IndividualHECTaxCheckData(
            IndividualApplicantDetails(GGCredId(""), Name("", ""), storedDateOfBirth),
            storedLicenceDetails,
            IndividualTaxDetails(
              NINO(""),
              Some(SAUTR("")),
              TaxSituation.SAPAYE,
              Some(YesNoAnswer.No),
              None
            ),
            taxCheckStartDateTime,
            HECTaxCheckSource.Digital
          ),
          taxCheckCode,
          TimeUtils.today().plusMonths(1L),
          now,
          false,
          None
        )

      val storedCompanyTaxCheck =
        HECTaxCheck(
          CompanyHECTaxCheckData(
            CompanyApplicantDetails(GGCredId(""), storedCRN, CompanyHouseName("Test Tech Ltd")),
            storedLicenceDetails,
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
          ),
          taxCheckCode,
          TimeUtils.today().plusMonths(1L),
          now,
          false,
          None
        )

      val matchingIndividualMatchRequest = HECTaxCheckMatchRequest(
        taxCheckCode,
        storedLicenceType,
        Right(storedDateOfBirth)
      )

      val matchingCompanyMatchRequest = HECTaxCheckMatchRequest(
        taxCheckCode,
        storedLicenceType,
        Left(storedCRN)
      )

      "return an error" when {

        "there is an error getting from the tax check store" in {
          mockGetTaxCheck(taxCheckCode)(Left(Error("")))

          val result = service.matchTaxCheck(matchingIndividualMatchRequest)
          await(result.value) shouldBe a[Left[_, _]]
        }

      }

      "return a 'no match' result" when {

        "no tax check exists with the given tax check code" in {

          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(None))
          mockTimeProviderNow(dateTime)

          val result = service.matchTaxCheck(matchingIndividualMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingIndividualMatchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the match request is for an individual but the stored tax check is for a company" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedCompanyTaxCheck)))
          mockTimeProviderNow(dateTime)

          val result = service.matchTaxCheck(matchingIndividualMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingIndividualMatchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the match request is for a company but the stored tax check is for an individual" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedIndividualTaxCheck)))
          mockTimeProviderNow(dateTime)

          val result = service.matchTaxCheck(matchingCompanyMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingCompanyMatchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the date of births in the match request and the stored tax check do not match" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedIndividualTaxCheck)))
          mockTimeProviderNow(dateTime)

          val matchRequest = matchingIndividualMatchRequest.copy(verifier = Right(incorrectDateOfBirth))
          val result       = service.matchTaxCheck(matchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the CRN's in the match request and the stored tax check do not match" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedCompanyTaxCheck)))
          mockTimeProviderNow(dateTime)

          val matchRequest = matchingCompanyMatchRequest.copy(verifier = Left(incorrectCRN))
          val result       = service.matchTaxCheck(matchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the date of births match but the licence types do not" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedIndividualTaxCheck)))
          mockTimeProviderNow(dateTime)

          val matchRequest = matchingIndividualMatchRequest.copy(licenceType = incorrectLicenceType)
          val result       = service.matchTaxCheck(matchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the CRN's match but the licence types do not" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedCompanyTaxCheck)))
          mockTimeProviderNow(dateTime)

          val matchRequest = matchingCompanyMatchRequest.copy(licenceType = incorrectLicenceType)
          val result       = service.matchTaxCheck(matchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

      }

      "return an 'expired' result" when {

        "all details match for an individual but the stored tax check has expired" in {
          val expiredTaxCheck = storedIndividualTaxCheck.copy(
            expiresAfter = TimeUtils.today().minusMonths(1L)
          )
          mockGetTaxCheck(taxCheckCode)(Right(Some(expiredTaxCheck)))
          mockTimeProviderNow(now)
          mockTimeProviderToday(today)

          val result = service.matchTaxCheck(matchingIndividualMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingIndividualMatchRequest, now, HECTaxCheckMatchStatus.Expired)
          )
        }

        "all details match for an company but the stored tax check has expired" in {
          val expiredTaxCheck = storedCompanyTaxCheck.copy(
            expiresAfter = TimeUtils.today().minusMonths(1L)
          )

          mockGetTaxCheck(taxCheckCode)(Right(Some(expiredTaxCheck)))
          mockTimeProviderNow(now)
          mockTimeProviderToday(today)

          val result = service.matchTaxCheck(matchingCompanyMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingCompanyMatchRequest, now, HECTaxCheckMatchStatus.Expired)
          )
        }

      }

      "return a 'match' result" when {

        "all details match for an individual and the stored tax check has not expired" in {
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedIndividualTaxCheck)))
          mockTimeProviderNow(now)
          mockTimeProviderToday(today)

          val result = service.matchTaxCheck(matchingIndividualMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingIndividualMatchRequest, now, HECTaxCheckMatchStatus.Match)
          )
        }

        "all details match for an company and the stored tax check has not expired" in {
          val taxCheck = storedCompanyTaxCheck.copy(expiresAfter = TimeUtils.today())

          mockTimeProviderNow(now)
          mockTimeProviderToday(today)

          mockGetTaxCheck(taxCheckCode)(Right(Some(taxCheck)))

          val result = service.matchTaxCheck(matchingCompanyMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingCompanyMatchRequest, now, HECTaxCheckMatchStatus.Match)
          )
        }

      }

    }

    "handling requests to fetch unexpired tax check codes" must {
      val ggCredId = GGCredId("ggCredId")
      val today    = LocalDate.of(2020, 1, 10)

      val taxCheckData = CompanyHECTaxCheckData(
        CompanyApplicantDetails(ggCredId, CRN(""), CompanyHouseName("Test Tech Ltd")),
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

      "return an error" when {
        "there is an error fetching the code from the db" in {
          val error = Left(Error("some error"))
          mockGetTaxCheckCodes(ggCredId)(error)
          mockTimeProviderToday(today)

          val result = service.getUnexpiredTaxCheckCodes(ggCredId)
          await(result.value) shouldBe error
        }
      }

      "only return unexpired tax check codes" in {
        val yesterday = today.minusDays(1)
        val tomorrow  = today.plusDays(1)

        val code1 = HECTaxCheckCode("code1")
        val code2 = HECTaxCheckCode("code2")
        val code3 = HECTaxCheckCode("code3")

        val taxCheckToday     = HECTaxCheck(taxCheckData, code1, today, now, false, None)
        val taxCheckYesterday = HECTaxCheck(taxCheckData, code2, yesterday, now, false, None)
        val taxCheckTomorrow  = HECTaxCheck(taxCheckData, code3, tomorrow, now, false, None)

        val todayItem    = TaxCheckListItem(
          taxCheckToday.taxCheckData.licenceDetails.licenceType,
          taxCheckToday.taxCheckCode,
          taxCheckToday.expiresAfter,
          taxCheckToday.createDate
        )
        val tomorrowItem = TaxCheckListItem(
          taxCheckTomorrow.taxCheckData.licenceDetails.licenceType,
          taxCheckTomorrow.taxCheckCode,
          taxCheckTomorrow.expiresAfter,
          taxCheckTomorrow.createDate
        )

        mockTimeProviderToday(today)
        mockGetTaxCheckCodes(ggCredId)(Right(List(taxCheckToday, taxCheckYesterday, taxCheckTomorrow)))

        val result = service.getUnexpiredTaxCheckCodes(ggCredId)
        await(result.value) shouldBe Right(List(todayItem, tomorrowItem))
      }
    }

    "handling requests to fetch HEC Tac check code not extracted before" must {
      val ggCredId = GGCredId("ggCredId")

      val taxCheckData: HECTaxCheckData = CompanyHECTaxCheckData(
        CompanyApplicantDetails(ggCredId, CRN(""), CompanyHouseName("Test Tech Ltd")),
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

      "return an error" when {

        "there is an error fetching the code from the db" in {
          val error = Left(Error("some error"))
          mockGetAllTaxCheckCodesByStatus(false)(error)

          val result = service.getAllTaxCheckCodesByExtractedStatus(false)
          await(result.value) shouldBe error
        }
      }

      "only return HEC Tac check code with isExtracted false" in {

        val hecTaxCheck1 =
          HECTaxCheck(taxCheckData, HECTaxCheckCode("ABC 123 ABC"), today.plusDays(1), now, false, None)
        val hecTaxCheck2 =
          HECTaxCheck(taxCheckData, HECTaxCheckCode("EBC 123 ABC"), today.plusDays(1), now, false, None)
        val hecTaxCheck3 =
          HECTaxCheck(taxCheckData, HECTaxCheckCode("MBC 123 ABC"), today.plusDays(1), now, false, None)

        mockGetAllTaxCheckCodesByStatus(false)(Right(List(hecTaxCheck1, hecTaxCheck2, hecTaxCheck3)))

        val result = service.getAllTaxCheckCodesByExtractedStatus(false)
        await(result.value) shouldBe Right(List(hecTaxCheck1, hecTaxCheck2, hecTaxCheck3))
      }
    }

  }

}
