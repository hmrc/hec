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

sealed trait CTStatus {
  val IFString: String
}
object CTStatus {
  case object ReturnFound extends CTStatus {
    override val IFString: String = "Return Found"
  }
  case object NoticeToFileIssued extends CTStatus {
    override val IFString: String = "Notice To File Issued"
  }
  case object NoAccountingPeriodFound extends CTStatus {
    override val IFString: String = "No Accounting Period Found"
  }
  case object NoReturnFound extends CTStatus {
    override val IFString: String = "No Return Found"
  }

  implicit val format: OFormat[CTStatus] = derived.oformat()

  def fromString(s: String): Option[CTStatus] = s match {
    case ReturnFound.IFString             => Some(ReturnFound)
    case NoticeToFileIssued.IFString      => Some(NoticeToFileIssued)
    case NoAccountingPeriodFound.IFString => Some(NoAccountingPeriodFound)
    case NoReturnFound.IFString           => Some(NoReturnFound)
    case _                                => None
  }
}