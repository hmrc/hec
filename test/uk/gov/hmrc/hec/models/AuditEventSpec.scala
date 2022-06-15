/*
 * Copyright 2022 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json
import uk.gov.hmrc.hec.models.AuditEvent.TaxCheckSuccess
import uk.gov.hmrc.hec.models.hecTaxCheck.ApplicantDetails.{CompanyApplicantDetails, IndividualApplicantDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxDetails.{CompanyTaxDetails, IndividualTaxDetails}
import uk.gov.hmrc.hec.models.hecTaxCheck.YesNoAnswer.{No, Yes}
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTAccountingPeriod, CTStatus, CTStatusResponse, CompanyHouseName}
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.{DateOfBirth, Name, SAStatus, SAStatusResponse}
import uk.gov.hmrc.hec.models.hecTaxCheck.{CorrectiveAction, HECTaxCheck, HECTaxCheckCode, HECTaxCheckSource, TaxSituation, TaxYear}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR, GGCredId, NINO, PID, SAUTR}

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import java.util.UUID

class AuditEventSpec extends Matchers with AnyWordSpecLike {

  "TaxCheckSuccess" should {

    "have the correct JSON format" when {

      val date = LocalDate.of(2020, 12, 1)

      val zonedDateTime = ZonedDateTime.of(
        2020,
        11,
        1,
        20,
        55,
        31,
        32,
        ZoneId.of("Europe/London")
      )

      val strideOperatorDetails = StrideOperatorDetails(
        PID("pid"),
        List("role1", "role2"),
        Some("name"),
        Some("email")
      )

      "given an individual" in {
        val auditEvent = TaxCheckSuccess(
          HECTaxCheck(
            IndividualHECTaxCheckData(
              IndividualApplicantDetails(
                Some(GGCredId("cred")),
                Name("First", "Last"),
                DateOfBirth(date)
              ),
              LicenceDetails(
                LicenceType.OperatorOfPrivateHireVehicles,
                LicenceTimeTrading.FourToEightYears,
                LicenceValidityPeriod.UpToFiveYears
              ),
              IndividualTaxDetails(
                NINO("nino"),
                Some(SAUTR("sautr")),
                TaxSituation.SAPAYE,
                Some(Yes),
                Some(
                  SAStatusResponse(
                    SAUTR("sautr"),
                    TaxYear(2020),
                    SAStatus.NoticeToFileIssued
                  )
                ),
                TaxYear(2021),
                Some(CorrectiveAction.RegisterNewSAAccount)
              ),
              zonedDateTime,
              HECTaxCheckSource.Digital,
              Some(Language.Welsh),
              None,
              None
            ),
            HECTaxCheckCode("code"),
            date.plusDays(1L),
            zonedDateTime.plusDays(1L),
            true,
            Some(UUID.randomUUID()),
            None
          ),
          Some(strideOperatorDetails)
        )

        Json.toJson(auditEvent) shouldBe Json.parse(
          s"""
             |{
             |   "taxCheckData" : {
             |      "applicantDetails": {
             |         "ggCredId": "cred",
             |         "name": { 
             |            "firstName": "First",
             |            "lastName": "Last"
             |         },
             |         "dateOfBirth": "2020-12-01"
             |      },
             |   "licenceDetails":{
             |      "licenceType": "OperatorOfPrivateHireVehicles",
             |      "licenceTimeTrading": "FourToEightYears",
             |      "licenceValidityPeriod": "UpToFiveYears"
             |   },
             |   "taxDetails": { 
             |      "nino": "nino",
             |      "sautr": "sautr",
             |      "taxSituation":"SAPAYE",
             |      "saIncomeDeclared": "Yes",
             |      "saStatusResponse": {
             |         "sautr": "sautr",
             |         "taxYear": 2020,
             |         "status": "NoticeToFileIssued"
             |      },
             |      "relevantIncomeTaxYear": 2021,
             |      "correctiveAction": "RegisterNewSAAccount"
             |   },
             |   "taxCheckStartDateTime": "2020-11-01T20:55:31.000000032Z[Europe/London]",
             |   "source": "Digital",
             |   "type": "Individual",
             |   "languagePreference": "Welsh"
             |   },    
             |   "taxCheckCode": "code",
             |   "expiresAfter": "2020-12-02",
             |   "createDate": "2020-11-02T20:55:31.000000032Z[Europe/London]",
             |   "operatorDetails": {
             |     "pid": "pid",
             |     "roles": [ "role1", "role2" ],
             |     "name": "name",
             |     "email": "email"
             |   }
             |}
             |""".stripMargin
        )
      }

      "given a company" in {
        val auditEvent = TaxCheckSuccess(
          HECTaxCheck(
            CompanyHECTaxCheckData(
              CompanyApplicantDetails(
                Some(GGCredId("cred")),
                CRN("crn"),
                CompanyHouseName("name")
              ),
              LicenceDetails(
                LicenceType.ScrapMetalDealerSite,
                LicenceTimeTrading.EightYearsOrMore,
                LicenceValidityPeriod.UpToOneYear
              ),
              CompanyTaxDetails(
                CTUTR("hmrcCTUTR"),
                Some(CTUTR("ctutr")),
                Some(Yes),
                CTStatusResponse(
                  CTUTR("ctutr"),
                  date,
                  date.plusDays(1L),
                  Some(
                    CTAccountingPeriod.CTAccountingPeriodStride(
                      date.plusDays(3L),
                      Some(CTStatus.ReturnFound)
                    )
                  )
                ),
                Some(No),
                Some(Yes),
                Some(CorrectiveAction.Other)
              ),
              zonedDateTime,
              HECTaxCheckSource.Stride,
              None,
              None,
              Some(true)
            ),
            HECTaxCheckCode("code"),
            date.plusDays(4L),
            zonedDateTime.plusDays(1L),
            false,
            Some(UUID.randomUUID()),
            None
          ),
          Some(strideOperatorDetails)
        )

        Json.toJson(auditEvent) shouldBe Json.parse(
          s"""
            |{
            |   "taxCheckData" : {
            |      "applicantDetails": {
            |         "ggCredId": "cred",
            |         "companyRegistrationNumber": "crn",
            |         "companyName": "name"
            |      },
            |      "licenceDetails": {
            |         "licenceType": "ScrapMetalDealerSite",
            |         "licenceTimeTrading": "EightYearsOrMore",
            |         "licenceValidityPeriod": "UpToOneYear"
            |      },
            |      "taxDetails": {
            |         "hmrcCTUTR" : "hmrcCTUTR",
            |         "userSuppliedCTUTR": "ctutr",
            |         "ctIncomeDeclared": "Yes", 
            |         "ctStatus": {
            |            "ctutr":  "ctutr",
            |            "startDate":  "2020-12-01",
            |            "endDate":  "2020-12-02",
            |            "latestAccountingPeriod": {
            |               "endDate": "2020-12-04",
            |               "ctStatus": "ReturnFound",
            |               "type": "Stride"
            |            }
            |         }, 
            |         "recentlyStaredTrading": "No",
            |         "chargeableForCT": "Yes",
            |         "correctiveAction": "Other"
            |      },
            |      "taxCheckStartDateTime": "2020-11-01T20:55:31.000000032Z[Europe/London]",
            |      "source": "Stride",
            |      "type": "Company",
            |      "filterFromFileTransfer": true
            |   },
            |   "taxCheckCode": "code",
            |   "expiresAfter": "2020-12-05",
            |   "createDate": "2020-11-02T20:55:31.000000032Z[Europe/London]",
            |   "operatorDetails": {
            |     "pid": "pid",
            |     "roles": [ "role1", "role2" ],
            |     "name": "name",
            |     "email": "email"
            |   }
            |}
            |""".stripMargin
        )

      }
    }

  }

}
