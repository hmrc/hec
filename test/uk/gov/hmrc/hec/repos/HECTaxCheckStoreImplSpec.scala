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

package uk.gov.hmrc.hec.repos

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.{JsNumber, JsObject}
import play.api.test.Helpers._
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.hec.models.ApplicantDetails.CompanyApplicantDetails
import uk.gov.hmrc.hec.models.HECTaxCheckData.CompanyHECTaxCheckData
import uk.gov.hmrc.hec.models.TaxDetails.CompanyTaxDetails
import uk.gov.hmrc.hec.models.{HECTaxCheck, HECTaxCheckCode}
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR, GGCredId}
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceExpiryDate, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.DatabaseUpdate

import java.time.LocalDate
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class HECTaxCheckStoreImplSpec extends AnyWordSpec with Matchers with Eventually with MongoSupport {

  val config = Configuration(
    ConfigFactory.parseString(
      """
        | hec-tax-check.ttl = 1 day
        |""".stripMargin
    )
  )

  val taxCheckStore = new HECTaxCheckStoreImpl(reactiveMongoComponent, config)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "HECTaxCheckStoreImpl" must {

    val taxCheckData = CompanyHECTaxCheckData(
      CompanyApplicantDetails(GGCredId(""), CRN("")),
      LicenceDetails(
        LicenceType.ScrapMetalDealerSite,
        LicenceExpiryDate(LocalDate.now()),
        LicenceTimeTrading.EightYearsOrMore,
        LicenceValidityPeriod.UpToOneYear
      ),
      CompanyTaxDetails(CTUTR(""))
    )

    val taxCheckCode = HECTaxCheckCode("code")
    val taxCheck     = HECTaxCheck(taxCheckData, taxCheckCode, TimeUtils.today())

    "be able to insert tax checks into mongo and read it back" in {

      await(taxCheckStore.store(taxCheck).value) shouldBe Right(())

      eventually {
        await(taxCheckStore.get(taxCheckCode).value) should be(Right(Some(taxCheck)))
      }
    }

    "return no SessionData if there is no data in mongo" in {
      await(taxCheckStore.get(HECTaxCheckCode("abc")).value) shouldBe Right(None)
    }

    "return an error" when {

      "the data in mongo cannot be parsed" in {
        val taxCheckCode                          = HECTaxCheckCode("invalid-data")
        val invalidData                           = JsObject(Map("hec-tax-check" -> JsNumber(1)))
        val create: Future[DatabaseUpdate[Cache]] =
          taxCheckStore.cacheRepository.createOrUpdate(
            Id(taxCheckCode.value),
            "hec-tax-check",
            invalidData
          )
        await(create).writeResult.inError                   shouldBe false
        await(taxCheckStore.get(taxCheckCode).value).isLeft shouldBe true
      }

    }
  }

}
