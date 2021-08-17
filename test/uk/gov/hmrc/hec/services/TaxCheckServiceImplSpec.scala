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
import uk.gov.hmrc.hec.models.ApplicantDetails.IndividualApplicantDetails
import uk.gov.hmrc.hec.models.{DateOfBirth, Error, HECTaxCheck, HECTaxCheckCode, HECTaxCheckData, Name}
import uk.gov.hmrc.hec.models.ids.{GGCredId, NINO, SAUTR}
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceExpiryDate, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class TaxCheckServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockTaxCheckCodeGeneratorService = mock[TaxCheckCodeGeneratorService]
  val mockTaxCheckStore                = mock[HECTaxCheckStore]
  val expiresAfter                     = 5.days

  val config = ConfigFactory.parseString(s"""{
      |hec-tax-check.expires-after = ${expiresAfter.toDays} days
      |}""".stripMargin)

  val service = new TaxCheckServiceImpl(mockTaxCheckCodeGeneratorService, mockTaxCheckStore, config)

  def mockGenerateTaxCheckCode(taxCheckCode: HECTaxCheckCode) =
    (mockTaxCheckCodeGeneratorService.next _).expects().returning(taxCheckCode)

  def mockStoreTaxCheck(taxCheck: HECTaxCheck)(result: Either[Error, Unit]) =
    (mockTaxCheckStore
      .store(_: HECTaxCheck)(_: HeaderCarrier))
      .expects(taxCheck, *)
      .returning(EitherT.fromEither(result))

  "TaxCheckServiceImpl" when {

    "handling requests to save a tax check" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      val taxCheckData = HECTaxCheckData(
        IndividualApplicantDetails(GGCredId(""), NINO(""), Some(SAUTR("")), Name("", ""), DateOfBirth(LocalDate.now())),
        LicenceDetails(
          LicenceType.ScrapMetalDealerSite,
          LicenceExpiryDate(LocalDate.now()),
          LicenceTimeTrading.EightYearsOrMore,
          LicenceValidityPeriod.UpToOneYear
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

  }

}
