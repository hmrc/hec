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

package uk.gov.hmrc.hec.models

import cats.implicits.catsSyntaxOptionId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.hec.models.hecTaxCheck.ApplicantDetails.{CompanyApplicantDetails, IndividualApplicantDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxDetails.{CompanyTaxDetails, IndividualTaxDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck._
import uk.gov.hmrc.hec.models.hecTaxCheck.company.CTAccountingPeriod.CTAccountingPeriodDigital
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTStatus, CTStatusResponse, CompanyHouseName}
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.{DateOfBirth, Name}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.ids._

import java.time.{LocalDate, ZoneId, ZonedDateTime}

class HECTaxCheckDataSpec extends AnyWordSpec with Matchers {

  "HECTaxCheckData" must {

    val taxCheckStartDateTime = ZonedDateTime.of(2021, 10, 9, 9, 12, 34, 0, ZoneId.of("Europe/London"))

    "perform JSON de/serialisation correctly" must {
      val dateOfBirthStr = "2000-10-10"
      val dateOfBirth    = LocalDate.of(2000, 10, 10)

      val individualTaxCheckData: HECTaxCheckData =
        IndividualHECTaxCheckData(
          IndividualApplicantDetails(
            Some(GGCredId("ggCredId")),
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
            Some(YesNoAnswer.Yes),
            None,
            TaxYear(2021),
            None
          ),
          taxCheckStartDateTime,
          HECTaxCheckSource.Digital,
          Some(Language.English),
          Some(true),
          Some(false)
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
                                         |    "saIncomeDeclared":"Yes",
                                         |    "relevantIncomeTaxYear": 2021
                                         | },
                                         | "taxCheckStartDateTime" : "2021-10-09T09:12:34+01:00[Europe/London]",
                                         | "type":"Individual",
                                         | "source": "Digital",
                                         | "languagePreference": "English",
                                         | "didConfirmUncertainEntityType": true,
                                         | "filterFromFileTransfer": false
                                         |}""".stripMargin)

      val companyTaxCheckData: HECTaxCheckData =
        CompanyHECTaxCheckData(
          CompanyApplicantDetails(GGCredId("ggCredId").some, CRN("12345678"), CompanyHouseName("Test Tech Ltd")),
          LicenceDetails(
            LicenceType.ScrapMetalMobileCollector,
            LicenceTimeTrading.EightYearsOrMore,
            LicenceValidityPeriod.UpToThreeYears
          ),
          CompanyTaxDetails(
            hmrcCTUTR = CTUTR("1111111111"),
            Some(CTUTR("1111111111")),
            ctIncomeDeclared = Some(YesNoAnswer.Yes),
            ctStatus = CTStatusResponse(
              ctutr = CTUTR("1111111111"),
              startDate = LocalDate.of(2020, 10, 9),
              endDate = LocalDate.of(2021, 10, 9),
              latestAccountingPeriod = Some(
                CTAccountingPeriodDigital(LocalDate.of(2020, 10, 9), LocalDate.of(2021, 10, 9), CTStatus.ReturnFound)
              )
            ),
            recentlyStaredTrading = None,
            Some(YesNoAnswer.Yes),
            None
          ),
          taxCheckStartDateTime,
          HECTaxCheckSource.Stride,
          None,
          None,
          None
        )

      val companyJson = Json.parse("""{
                                     | "applicantDetails":{
                                     |   "ggCredId":"ggCredId",
                                     |   "crn":"12345678",
                                     |   "companyName":"Test Tech Ltd"
                                     | },
                                     | "licenceDetails":{
                                     |   "licenceType":"ScrapMetalMobileCollector",
                                     |   "licenceTimeTrading":"EightYearsOrMore",
                                     |   "licenceValidityPeriod":"UpToThreeYears"
                                     | },
                                     | "taxDetails":{
                                         "hmrcCTUTR":"1111111111",
                                     |   "userSuppliedCTUTR":"1111111111",
                                     |   "ctIncomeDeclared" : "Yes",
                                     |   "ctStatus": {
                                     |      "ctutr":"1111111111",
                                     |      "startDate":"2020-10-09",
                                     |      "endDate":"2021-10-09",
                                     |      "latestAccountingPeriod" : {
                                     |          "startDate":"2020-10-09",
                                     |           "endDate":"2021-10-09",
                                     |           "ctStatus":"ReturnFound",
                                     |           "type":"Digital"
                                     |      }
                                     |   },
                                     |   "chargeableForCT" : "Yes"
                                     | },
                                     | "taxCheckStartDateTime" : "2021-10-09T09:12:34+01:00[Europe/London]",
                                     | "source": "Stride",
                                     |  "type":"Company"
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
