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

import enumeratum.{EnumEntry, PlayEnum}

sealed trait MatchFailureReason extends EnumEntry

object MatchFailureReason extends PlayEnum[MatchFailureReason] {

  override val values: IndexedSeq[MatchFailureReason] = findValues

  case object TaxCheckCodeNotMatched extends MatchFailureReason
  case object EntityTypeNotMatched extends MatchFailureReason
  case object DateOfBirthNotMatched extends MatchFailureReason
  case object CRNNotMatched extends MatchFailureReason
  case object LicenceTypeNotMatched extends MatchFailureReason
  case object LicenceTypeEntityTypeNotMatched extends MatchFailureReason
  case object LicenceTypeDateOfBirthNotMatched extends MatchFailureReason
  case object LicenceTypeCRNNotMatched extends MatchFailureReason
}
