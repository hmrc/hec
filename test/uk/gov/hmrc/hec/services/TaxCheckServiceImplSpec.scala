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
import uk.gov.hmrc.hec.models.{DateOfBirth, Error, HECTaxCheck, HECTaxCheckCode, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, HECTaxCheckMatchStatus, IncomeDeclared, Name, TaxSituation}
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR, GGCredId, NINO, SAUTR}
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.util.{TimeProvider, TimeUtils}
import uk.gov.hmrc.http.HeaderCarrier
import java.time.{LocalDate, ZonedDateTime}

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

  def mockTimeProviderToday(d: ZonedDateTime) = (mockTimeProvider.currentDateTime _).expects().returning(d)

  implicit val hc: HeaderCarrier = HeaderCarrier()

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
          Some(IncomeDeclared.Yes)
        )
      )

      val expectedExpiryDate = TimeUtils.today().plusDays(expiresAfter.toDays)
      val taxCheckCode       = HECTaxCheckCode("code")
      val taxCheck           = HECTaxCheck(taxCheckData, taxCheckCode, expectedExpiryDate)

      "return an error" when {

        "there is an error saving the tax check" in {
          inSequence {
            mockGenerateTaxCheckCode(taxCheckCode)
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
              Some(IncomeDeclared.No)
            )
          ),
          taxCheckCode,
          TimeUtils.today().plusMonths(1L)
        )

      val storedCompanyTaxCheck =
        HECTaxCheck(
          CompanyHECTaxCheckData(
            CompanyApplicantDetails(GGCredId(""), storedCRN),
            storedLicenceDetails,
            CompanyTaxDetails(CTUTR(""))
          ),
          taxCheckCode,
          TimeUtils.today().plusMonths(1L)
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
          mockTimeProviderToday(dateTime)

          val result = service.matchTaxCheck(matchingIndividualMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingIndividualMatchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the match request is for an individual but the stored tax check is for a company" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedCompanyTaxCheck)))
          mockTimeProviderToday(dateTime)

          val result = service.matchTaxCheck(matchingIndividualMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingIndividualMatchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the match request is for a company but the stored tax check is for an individual" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedIndividualTaxCheck)))
          mockTimeProviderToday(dateTime)

          val result = service.matchTaxCheck(matchingCompanyMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingCompanyMatchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the date of births in the match request and the stored tax check do not match" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedIndividualTaxCheck)))
          mockTimeProviderToday(dateTime)

          val matchRequest = matchingIndividualMatchRequest.copy(verifier = Right(incorrectDateOfBirth))
          val result       = service.matchTaxCheck(matchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the CRN's in the match request and the stored tax check do not match" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedCompanyTaxCheck)))
          mockTimeProviderToday(dateTime)

          val matchRequest = matchingCompanyMatchRequest.copy(verifier = Left(incorrectCRN))
          val result       = service.matchTaxCheck(matchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the date of births match but the licence types do not" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedIndividualTaxCheck)))
          mockTimeProviderToday(dateTime)

          val matchRequest = matchingIndividualMatchRequest.copy(licenceType = incorrectLicenceType)
          val result       = service.matchTaxCheck(matchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchRequest, dateTime, HECTaxCheckMatchStatus.NoMatch)
          )
        }

        "the CRN's match but the licence types do not" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedCompanyTaxCheck)))
          mockTimeProviderToday(dateTime)

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
          val dateTime        = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(expiredTaxCheck)))
          mockTimeProviderToday(dateTime)

          val result = service.matchTaxCheck(matchingIndividualMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingIndividualMatchRequest, dateTime, HECTaxCheckMatchStatus.Expired)
          )
        }

        "all details match for an company but the stored tax check has expired" in {
          val expiredTaxCheck = storedCompanyTaxCheck.copy(
            expiresAfter = TimeUtils.today().minusMonths(1L)
          )

          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(expiredTaxCheck)))
          mockTimeProviderToday(dateTime)

          val result = service.matchTaxCheck(matchingCompanyMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingCompanyMatchRequest, dateTime, HECTaxCheckMatchStatus.Expired)
          )
        }

      }

      "return an 'match' result" when {

        "all details match for an individual and the stored tax check has not expired" in {
          val dateTime = TimeUtils.now()
          mockGetTaxCheck(taxCheckCode)(Right(Some(storedIndividualTaxCheck)))
          mockTimeProviderToday(dateTime)

          val result = service.matchTaxCheck(matchingIndividualMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingIndividualMatchRequest, dateTime, HECTaxCheckMatchStatus.Match)
          )
        }

        "all details match for an company and the stored tax check has not expired" in {
          val taxCheck = storedCompanyTaxCheck.copy(expiresAfter = TimeUtils.today())
          val dateTime = TimeUtils.now()

          mockTimeProviderToday(dateTime)
          mockGetTaxCheck(taxCheckCode)(Right(Some(taxCheck)))

          val result = service.matchTaxCheck(matchingCompanyMatchRequest)
          await(result.value) shouldBe Right(
            HECTaxCheckMatchResult(matchingCompanyMatchRequest, dateTime, HECTaxCheckMatchStatus.Match)
          )
        }

      }

    }

  }

}
