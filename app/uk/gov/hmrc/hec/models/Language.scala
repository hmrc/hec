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

import play.api.libs.json.{Format, JsError, JsString, JsSuccess, Reads, Writes}

sealed trait Language extends Product with Serializable

object Language {

  case object English extends Language

  case object Welsh extends Language

  implicit val format: Format[Language] = Format(
    Reads {
      case JsString("English") => JsSuccess(English)
      case JsString("Welsh")   => JsSuccess(Welsh)
      case JsString(other)     => JsError(s"Found unsupported language $other")
      case other               => JsError(s"Expected string but found ${other.getClass.getSimpleName}")
    },
    Writes(l => JsString(l.toString))
  )

}
