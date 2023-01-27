/*
 * Copyright 2023 HM Revenue & Customs
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

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json
import play.api.mvc.{Headers, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.hec.models.AuditEvent.TaxCheckSuccess
import uk.gov.hmrc.hec.models.Language
import uk.gov.hmrc.hec.models.hecTaxCheck.ApplicantDetails.CompanyApplicantDetails
import uk.gov.hmrc.hec.models.hecTaxCheck.{HECTaxCheck, HECTaxCheckCode, HECTaxCheckSource}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.CompanyHECTaxCheckData
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxDetails.CompanyTaxDetails
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTStatusResponse, CompanyHouseName}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.{LicenceDetails, LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.ids.{CRN, CTUTR}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.{LocalDate, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class AuditServiceImplSpec extends Matchers with AnyWordSpecLike with MockFactory {

  val mockAuditConnector = mock[AuditConnector]

  def mockSendExtendedEvent(expectedEvent: ExtendedDataEvent)(result: Future[AuditResult]) =
    (mockAuditConnector
      .sendExtendedEvent(_: ExtendedDataEvent)(_: HeaderCarrier, _: ExecutionContext))
      .expects(where[ExtendedDataEvent, HeaderCarrier, ExecutionContext] {
        case (actualEvent: ExtendedDataEvent, _: HeaderCarrier, _: ExecutionContext) =>
          actualEvent.auditType === expectedEvent.auditType
          actualEvent.auditSource === expectedEvent.auditSource
          actualEvent.detail === expectedEvent.detail
          actualEvent.tags === expectedEvent.tags
      })
      .returning(result)

  val service = new AuditServiceImpl(mockAuditConnector)

  "AuditServiceImpl" when {

    "handling requests to audit an event" must {

      "return successfully" when {

        val requestUri = "/uri"

        implicit val request: Request[_] = FakeRequest("GET", requestUri, Headers(), "")

        implicit val hc: HeaderCarrier = HeaderCarrier()

        val auditEvent = TaxCheckSuccess(
          HECTaxCheck(
            CompanyHECTaxCheckData(
              CompanyApplicantDetails(
                None,
                CRN(""),
                CompanyHouseName("")
              ),
              LicenceDetails(
                LicenceType.ScrapMetalDealerSite,
                LicenceTimeTrading.EightYearsOrMore,
                LicenceValidityPeriod.UpToOneYear
              ),
              CompanyTaxDetails(
                CTUTR(""),
                None,
                None,
                CTStatusResponse(
                  CTUTR(""),
                  LocalDate.now(),
                  LocalDate.now(),
                  None
                ),
                None,
                None,
                None
              ),
              ZonedDateTime.now(),
              HECTaxCheckSource.Stride,
              Some(Language.Welsh),
              Some(false),
              None
            ),
            HECTaxCheckCode(""),
            LocalDate.now(),
            ZonedDateTime.now(),
            false,
            None,
            None
          ),
          None
        )

        val extendedDataEvent = ExtendedDataEvent(
          auditSource = "hidden-economy-conditionality",
          auditType = auditEvent.auditType,
          detail = Json.toJson(auditEvent),
          tags = hc.toAuditTags(auditEvent.transactionName, requestUri)
        )

        "a 'Successful' AuditResult is given" in {
          mockSendExtendedEvent(extendedDataEvent)(Future.successful(AuditResult.Success))

          service.sendEvent(auditEvent) shouldBe (())
        }

        "a 'Disabled' audit result is given" in {
          mockSendExtendedEvent(extendedDataEvent)(Future.successful(AuditResult.Disabled))

          service.sendEvent(auditEvent) shouldBe (())
        }

        "a 'Failure' audit result is given" in {
          mockSendExtendedEvent(extendedDataEvent)(Future.successful(AuditResult.Failure("")))

          service.sendEvent(auditEvent) shouldBe (())
        }

        "the call to audit fails" in {
          mockSendExtendedEvent(extendedDataEvent)(Future.failed(new Exception("")))

          service.sendEvent(auditEvent) shouldBe (())
        }

      }

    }

  }

}
