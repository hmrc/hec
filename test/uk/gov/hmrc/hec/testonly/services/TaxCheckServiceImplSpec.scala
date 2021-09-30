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

package uk.gov.hmrc.hec.testonly.services

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers._
import uk.gov.hmrc.hec.models.ApplicantDetails.IndividualApplicantDetails
import uk.gov.hmrc.hec.models.HECTaxCheckData.IndividualHECTaxCheckData
import uk.gov.hmrc.hec.models.TaxDetails.IndividualTaxDetails
import uk.gov.hmrc.hec.models.ids.{CRN, GGCredId, NINO, SAUTR}
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.{DateOfBirth, Error, HECTaxCheck, HECTaxCheckCode, IncomeDeclared, Name, TaxSituation}
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.testonly.models.SaveTaxCheckRequest
import uk.gov.hmrc.hec.util.{TimeProvider, TimeUtils}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class TaxCheckServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockTaxCheckStore = mock[HECTaxCheckStore]

  val service = new TaxCheckServiceImpl(mockTaxCheckStore)

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

  def mockDeleteTaxCheck(taxCheckCode: HECTaxCheckCode)(result: Either[Error, Unit]) =
    (mockTaxCheckStore
      .delete(_: HECTaxCheckCode)(_: HeaderCarrier))
      .expects(taxCheckCode, *)
      .returning(EitherT.fromEither(result))

  def mockDeleteAllTaxChecks(result: Either[Error, Unit]) =
    (mockTaxCheckStore
      .deleteAll()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither(result))

  val mockTimeProvider = mock[TimeProvider]

  def mockTimeProviderToday(d: LocalDate) = (mockTimeProvider.currentDate _).expects().returning(d)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val now   = TimeUtils.now()
  private val today = TimeUtils.today()

  "TaxCheckServiceImpl" when {

    "handling requests to save a tax check" must {

      def saveTaxCheckRequest(verifier: Either[CRN, DateOfBirth]) =
        SaveTaxCheckRequest(
          HECTaxCheckCode("ABCDEF234"),
          LicenceType.ScrapMetalDealerSite,
          verifier,
          today,
          now,
          false
        )

      "return an error" when {

        "there is an error saving the tax check" in {
          val request  = saveTaxCheckRequest(Left(CRN("crn")))
          val taxCheck = HECTaxCheck(
            service.taxCheckData(request),
            request.taxCheckCode,
            request.expiresAfter,
            now,
            false
          )

          inSequence {
            mockStoreTaxCheck(taxCheck)(Left(Error("")))
          }

          val result = service.saveTaxCheck(request).value
          await(result) shouldBe a[Left[_, _]]
        }

      }

      "return successfully" when {

        "the tax check has been saved for an individual" in {
          val request  = saveTaxCheckRequest(Right(DateOfBirth(TimeUtils.today())))
          val taxCheck = HECTaxCheck(
            service.taxCheckData(request),
            request.taxCheckCode,
            request.expiresAfter,
            now,
            false
          )

          inSequence {
            mockStoreTaxCheck(taxCheck)(Right(()))
          }

          val result = service.saveTaxCheck(request).value
          await(result) shouldBe Right(())
        }

        "the tax check has been saved for a company" in {
          val request  = saveTaxCheckRequest(Left(CRN("crn")))
          val taxCheck = HECTaxCheck(
            service.taxCheckData(request),
            request.taxCheckCode,
            request.expiresAfter,
            now,
            false
          )

          inSequence {
            mockStoreTaxCheck(taxCheck)(Right(()))
          }

          val result = service.saveTaxCheck(request).value
          await(result) shouldBe Right(())
        }

      }

    }

    "handling requests to get a tax check" must {

      val taxCheckCode = HECTaxCheckCode("code")

      "return an error when there is an error getting the tax check" in {
        mockGetTaxCheck(taxCheckCode)(Left(Error("")))

        val result = service.getTaxCheck(taxCheckCode)
        await(result.value) shouldBe a[Left[_, _]]
      }

      "return successfully" when {

        "a tax check is found" in {
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
          val taxCheck     = HECTaxCheck(taxCheckData, taxCheckCode, TimeUtils.today(), now, false)

          mockGetTaxCheck(taxCheckCode)(Right(Some(taxCheck)))

          val result = service.getTaxCheck(taxCheckCode)
          await(result.value) shouldBe Right(Some(taxCheck))
        }

        "a tax check is not found" in {
          mockGetTaxCheck(taxCheckCode)(Right(None))

          val result = service.getTaxCheck(taxCheckCode)
          await(result.value) shouldBe Right(None)
        }

      }
    }

    "handling requests to delete a tax check" must {

      val taxCheckCode = HECTaxCheckCode("code")

      "return an error when there is an error deleting" in {
        mockDeleteTaxCheck(taxCheckCode)(Left(Error("")))

        val result = service.deleteTaxCheck(taxCheckCode)
        await(result.value) shouldBe a[Left[_, _]]
      }

      "return successfully if the deletion was successful" in {
        mockDeleteTaxCheck(taxCheckCode)(Right(()))

        val result = service.deleteTaxCheck(taxCheckCode)
        await(result.value) shouldBe Right(())
      }

    }

    "handling requests to delete all tax checks" must {

      "return an error when there is an error deleting" in {
        mockDeleteAllTaxChecks(Left(Error("")))

        val result = service.deleteAllTaxCheck()
        await(result.value) shouldBe a[Left[_, _]]
      }

      "return successfully if the deletion was successful" in {
        mockDeleteAllTaxChecks(Right(()))

        val result = service.deleteAllTaxCheck()
        await(result.value) shouldBe Right(())
      }

    }

  }

}
