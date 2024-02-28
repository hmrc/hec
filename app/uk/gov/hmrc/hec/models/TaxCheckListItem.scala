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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.LicenceType
import uk.gov.hmrc.hec.models.hecTaxCheck.{HECTaxCheck, HECTaxCheckCode}

import java.time.{LocalDate, ZonedDateTime}

final case class TaxCheckListItem(
  licenceType: LicenceType,
  taxCheckCode: HECTaxCheckCode,
  expiresAfter: LocalDate,
  createDate: ZonedDateTime
)

object TaxCheckListItem {
  implicit val format: OFormat[TaxCheckListItem] = Json.format

  def fromHecTaxCheck(taxCheck: HECTaxCheck): TaxCheckListItem = TaxCheckListItem(
    licenceType = taxCheck.taxCheckData.licenceDetails.licenceType,
    taxCheckCode = taxCheck.taxCheckCode,
    expiresAfter = taxCheck.expiresAfter,
    createDate = taxCheck.createDate
  )
}
