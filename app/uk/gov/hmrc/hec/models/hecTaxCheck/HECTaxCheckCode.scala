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

package uk.gov.hmrc.hec.models.hecTaxCheck

import play.api.libs.json.{Format, Json}

final case class HECTaxCheckCode(value: String) extends AnyVal

object HECTaxCheckCode {

  implicit val format: Format[HECTaxCheckCode] = Json.valueFormat

  val validCharacters: List[Char] = {
    val allowedLetters = ('A' to 'Z').toList.diff(List('I', 'O', 'S', 'U', 'V', 'W'))
    val allowedDigits  = ('0' to '9').toList.diff(List('0', '1', '5'))
    allowedLetters ::: allowedDigits
  }

  val validLength: Int = 9

}
