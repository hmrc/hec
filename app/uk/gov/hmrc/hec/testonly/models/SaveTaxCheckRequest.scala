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

package uk.gov.hmrc.hec.testonly.models

import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.hec.models.{DateOfBirth, HECTaxCheckCode}
import uk.gov.hmrc.hec.models.licence.LicenceType
import uk.gov.hmrc.hec.models.ids.CRN
import uk.gov.hmrc.hec.models.EitherUtils.eitherFormat

import java.time.{LocalDate, ZonedDateTime}
import java.time.format.DateTimeFormatter

final case class SaveTaxCheckRequest(
  taxCheckCode: HECTaxCheckCode,
  licenceType: LicenceType,
  verifier: Either[CRN, DateOfBirth],
  expiresAfter: LocalDate,
  createDate: ZonedDateTime
)

object SaveTaxCheckRequest {

  implicit val localDateReads: Reads[LocalDate] = Reads.localDateReads(DateTimeFormatter.BASIC_ISO_DATE)

  implicit val reads: Reads[SaveTaxCheckRequest] = Json.reads

}
