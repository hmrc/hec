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
import cats.Eq
import enumeratum._
import play.api.libs.json.Format
import uk.gov.hmrc.hec.controllers.FileType

import scala.collection.immutable

sealed trait LicenceType extends EnumEntry with Product with Serializable

object LicenceType extends Enum[LicenceType] with FileType {

  case object DriverOfTaxisAndPrivateHires extends LicenceType

  case object OperatorOfPrivateHireVehicles extends LicenceType

  case object ScrapMetalMobileCollector extends LicenceType

  case object ScrapMetalDealerSite extends LicenceType

  val values: immutable.IndexedSeq[LicenceType] = findValues

  @SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.Equals"))
  implicit val format: Format[LicenceType] = Jsonx.formatSealed[LicenceType]

  implicit val eq: Eq[LicenceType] = Eq.fromUniversalEquals

}
