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

import julienrf.json.derived
import play.api.libs.json.OFormat

sealed trait HECTaxCheckMatchResult

object HECTaxCheckMatchResult {

  final case class NoMatch(matchRequest: HECTaxCheckMatchRequest) extends HECTaxCheckMatchResult

  final case class Match(matchRequest: HECTaxCheckMatchRequest) extends HECTaxCheckMatchResult

  final case class Expired(matchRequest: HECTaxCheckMatchRequest) extends HECTaxCheckMatchResult

  implicit val format: OFormat[HECTaxCheckMatchResult] = derived.oformat()

}
