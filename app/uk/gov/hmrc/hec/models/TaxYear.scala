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

import cats.instances.int._
import cats.syntax.eq._
import play.api.libs.json.{Format, Json}

final case class TaxYear(startYear: Int) extends AnyVal

object TaxYear {
  implicit val format: Format[TaxYear] = Json.valueFormat

  def fromString(startYearStr: String): Option[TaxYear] =
    try if (startYearStr.length === 4) Some(TaxYear(startYearStr.toInt)) else None
    catch {
      case _: Exception => None
    }
}
