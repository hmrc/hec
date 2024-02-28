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

package uk.gov.hmrc.hec.models.taxCheckMatch

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckCode
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.DateOfBirth
import uk.gov.hmrc.hec.models.ids.CRN
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.LicenceType
import uk.gov.hmrc.hec.util.EitherUtils.eitherFormat

final case class HECTaxCheckMatchRequest(
  taxCheckCode: HECTaxCheckCode,
  licenceType: LicenceType,
  verifier: Either[CRN, DateOfBirth]
)

object HECTaxCheckMatchRequest {

  implicit val format: OFormat[HECTaxCheckMatchRequest] = Json.format

}
