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

package uk.gov.hmrc.hec.models.hecTaxCheck.licence

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed trait LicenceValidityPeriod extends EnumEntry

object LicenceValidityPeriod extends PlayEnum[LicenceValidityPeriod] {

  case object UpToOneYear extends LicenceValidityPeriod
  case object UpToTwoYears extends LicenceValidityPeriod
  case object UpToThreeYears extends LicenceValidityPeriod
  case object UpToFourYears extends LicenceValidityPeriod
  case object UpToFiveYears extends LicenceValidityPeriod

  val values: immutable.IndexedSeq[LicenceValidityPeriod] = findValues
}
