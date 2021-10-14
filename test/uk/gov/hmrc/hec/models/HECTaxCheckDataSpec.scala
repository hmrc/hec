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

package uk.gov.hmrc.hec.models

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.hec.models.ApplicantDetails.{CompanyApplicantDetails, IndividualApplicantDetails}
import uk.gov.hmrc.hec.models.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.TaxDetails.{CompanyTaxDetails, IndividualTaxDetails}
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR, GGCredId, NINO, SAUTR}
import uk.gov.hmrc.hec.models.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}

class HECTaxCheckDataSpec extends AnyWordSpec with Matchers {

  "HECTaxCheckData" must {

    val taxCheckStartDateTime = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("GMT"))

    "perform JSON de/serialisation correctly" must {
      val dateOfBirthStr = "20001010"
      val dateOfBirth    = LocalDate.of(2000, 10, 10)

      val individualTaxCheckData: HECTaxCheckData =
        IndividualHECTaxCheckData(
          IndividualApplicantDetails(
            GGCredId("ggCredId"),
            Name("first", "last"),
            DateOfBirth(dateOfBirth)
          ),
          LicenceDetails(
            LicenceType.ScrapMetalMobileCollector,
            LicenceTimeTrading.EightYearsOrMore,
            LicenceValidityPeriod.UpToThreeYears
          ),
          IndividualTaxDetails(
            NINO("nino"),
            Some(SAUTR("utr")),
            TaxSituation.SA,
            Some(IncomeDeclared.Yes),
            None
          ),
          taxCheckStartDateTime,
          HECTaxCheckSource.Digital
        )

      val individualJson = Json.parse(s"""{
                                         | "applicantDetails":{
                                         |    "ggCredId":"ggCredId",
                                         |    "name":{
                                         |      "firstName":"first",
                                         |      "lastName":"last"
                                         |    },
                                         |    "dateOfBirth":"$dateOfBirthStr"
                                         | },
                                         | "licenceDetails":{
                                         |    "licenceType":"ScrapMetalMobileCollector",
                                         |    "licenceTimeTrading":"EightYearsOrMore",
                                         |    "licenceValidityPeriod":"UpToThreeYears"
                                         | },
                                         | "taxDetails":{
                                         |    "nino":"nino",
                                         |    "sautr":"utr",
                                         |    "taxSituation":"SA",
                                         |    "saIncomeDeclared":"Yes"
                                         | },
                                         | "taxCheckStartDateTime" : "2021-10-09T09:12:34Z[GMT]",
                                         | "type":"Individual",
                                         | "source": "Digital"
                                         |}""".stripMargin)

      val companyTaxCheckData: HECTaxCheckData =
        CompanyHECTaxCheckData(
          CompanyApplicantDetails(
            GGCredId("ggCredId"),
            CRN("crn")
          ),
          LicenceDetails(
            LicenceType.ScrapMetalMobileCollector,
            LicenceTimeTrading.EightYearsOrMore,
            LicenceValidityPeriod.UpToThreeYears
          ),
          CompanyTaxDetails(
            CTUTR("utr")
          ),
          taxCheckStartDateTime,
          HECTaxCheckSource.Stride
        )

      val companyJson = Json.parse("""{
                                     | "applicantDetails":{
                                     |   "ggCredId":"ggCredId",
                                     |   "crn":"crn"
                                     | },
                                     | "licenceDetails":{
                                     |   "licenceType":"ScrapMetalMobileCollector",
                                     |   "licenceTimeTrading":"EightYearsOrMore",
                                     |   "licenceValidityPeriod":"UpToThreeYears"
                                     | },
                                     | "taxDetails":{
                                     |   "ctutr":"utr"
                                     | },
                                     | "taxCheckStartDateTime" : "2021-10-09T09:12:34Z[GMT]",
                                     | "type":"Company",
                                     | "source": "Stride"
                                     |}""".stripMargin)

      "serialize Individual data" in {
        Json.toJson(individualTaxCheckData) shouldBe individualJson
      }

      "serialize Company data" in {
        Json.toJson(companyTaxCheckData) shouldBe companyJson
      }

      "deserialize Individual data" in {
        Json.fromJson[HECTaxCheckData](individualJson).get shouldBe individualTaxCheckData
      }

      "deserialize Company data" in {
        Json.fromJson[HECTaxCheckData](companyJson).get shouldBe companyTaxCheckData
      }
    }
  }

}
