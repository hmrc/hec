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

import uk.gov.hmrc.hec.models.licence.LicenceType
import uk.gov.hmrc.hec.testonly.models.SaveTaxCheckRequest

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers._
import uk.gov.hmrc.hec.models.{DateOfBirth, Error, HECTaxCheck, HECTaxCheckCode}
import uk.gov.hmrc.hec.models.ids.CRN
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class TaxCheckServiceImplSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockTaxCheckStore = mock[HECTaxCheckStore]

  val service = new TaxCheckServiceImpl(mockTaxCheckStore)

  def mockStoreTaxCheck(taxCheck: HECTaxCheck)(result: Either[Error, Unit]) =
    (mockTaxCheckStore
      .store(_: HECTaxCheck)(_: HeaderCarrier))
      .expects(taxCheck, *)
      .returning(EitherT.fromEither(result))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "TaxCheckServiceImpl" when {

    "handling requests to save a tax check" must {

      def saveTaxCheckRequest(verifier: Either[CRN, DateOfBirth]) =
        SaveTaxCheckRequest(
          HECTaxCheckCode("ABCDEF234"),
          LicenceType.ScrapMetalDealerSite,
          verifier,
          TimeUtils.today()
        )

      "return an error" when {

        "there is an error saving the tax check" in {
          val request  = saveTaxCheckRequest(Left(CRN("crn")))
          val taxCheck = HECTaxCheck(
            service.taxCheckData(request),
            request.taxCheckCode,
            request.expiresAfter
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
            request.expiresAfter
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
            request.expiresAfter
          )

          inSequence {
            mockStoreTaxCheck(taxCheck)(Right(()))
          }

          val result = service.saveTaxCheck(request).value
          await(result) shouldBe Right(())
        }

      }

    }
  }
}
