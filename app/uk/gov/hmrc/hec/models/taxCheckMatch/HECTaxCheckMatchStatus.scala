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

import play.api.libs.json._

sealed trait HECTaxCheckMatchStatus extends Product with Serializable

object HECTaxCheckMatchStatus {

  final case class NoMatch(failureReason: MatchFailureReason) extends HECTaxCheckMatchStatus
  case object Match extends HECTaxCheckMatchStatus
  case object Expired extends HECTaxCheckMatchStatus

  implicit val format: Format[HECTaxCheckMatchStatus] = {

    val noMatchFormat: OFormat[NoMatch] = Json.format

    val reads: Reads[HECTaxCheckMatchStatus] = Reads {
      case JsString("Match")   => JsSuccess(Match)
      case JsString("Expired") => JsSuccess(Expired)
      case value               => noMatchFormat.reads(value)
    }

    val writes: Writes[HECTaxCheckMatchStatus] = Writes {
      case Match          => JsString("Match")
      case Expired        => JsString("Expired")
      case value: NoMatch => noMatchFormat.writes(value)
    }

    Format(reads, writes)
  }
}
