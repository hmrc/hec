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

package uk.gov.hmrc.hec.testonly.models

import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.DateOfBirth
import uk.gov.hmrc.hec.models.ids.{CRN, GGCredId}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.LicenceType
import uk.gov.hmrc.hec.util.EitherUtils.eitherFormat
import uk.gov.hmrc.hec.models.hecTaxCheck.{CorrectiveAction, HECTaxCheckCode, HECTaxCheckSource, TaxYear}

import java.time.{LocalDate, ZonedDateTime}

final case class SaveTaxCheckRequest(
  taxCheckCode: HECTaxCheckCode,
  ggCredId: GGCredId,
  licenceType: LicenceType,
  verifier: Either[CRN, DateOfBirth],
  expiresAfter: LocalDate,
  createDate: ZonedDateTime,
  taxCheckStartDateTime: ZonedDateTime,
  isExtracted: Boolean,
  source: HECTaxCheckSource,
  relevantIncomeTaxYear: Option[TaxYear],
  correctiveAction: Option[CorrectiveAction]
)

object SaveTaxCheckRequest {

  implicit val reads: Reads[SaveTaxCheckRequest] = Json.reads

}
