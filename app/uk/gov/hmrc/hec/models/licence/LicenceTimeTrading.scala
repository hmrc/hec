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

package uk.gov.hmrc.hec.models.licence

import ai.x.play.json.Jsonx
import ai.x.play.json.SingletonEncoder.simpleName
import ai.x.play.json.implicits.formatSingleton
import enumeratum.{Enum, EnumEntry}
import play.api.libs.json.Format
import uk.gov.hmrc.hec.models.fileFormat.EnumFileBody

import scala.collection.immutable

sealed trait LicenceTimeTrading extends EnumEntry with Product with Serializable

object LicenceTimeTrading extends Enum[LicenceTimeTrading] {

  case object ZeroToTwoYears extends LicenceTimeTrading

  case object TwoToFourYears extends LicenceTimeTrading

  case object FourToEightYears extends LicenceTimeTrading

  case object EightYearsOrMore extends LicenceTimeTrading

  val values: immutable.IndexedSeq[LicenceTimeTrading] = findValues

  @SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.Equals"))
  implicit val format: Format[LicenceTimeTrading] = Jsonx.formatSealed[LicenceTimeTrading]

  def enumKeysAndValue(licenceTimeTrading: LicenceTimeTrading): (String, String) = licenceTimeTrading match {
    case ZeroToTwoYears   => ("00", "Zero to two years")
    case TwoToFourYears   => ("01", "Two to four years")
    case FourToEightYears => ("02", "Four to eight years")
    case EightYearsOrMore => ("03", "More than eight years")
  }

  def toEnumFileBody: List[EnumFileBody] =
    values
      .map(enumKeysAndValue)
      .map(keyValue => EnumFileBody(recordId = keyValue._1, recordDescription = keyValue._2))
      .toList

}
