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

sealed trait SAStatus extends Product with Serializable {
  val IFString: String
}

object SAStatus {
  case object ReturnFound extends SAStatus {
    override val IFString: String = "Return Found"
  }
  case object NoticeToFileIssued extends SAStatus {
    override val IFString: String = "Notice to File Issued"
  }
  case object NoReturnFound extends SAStatus {
    override val IFString: String = "No Return Found"
  }

  implicit val format: OFormat[SAStatus] = derived.oformat()

  def fromString(s: String): Option[SAStatus] = s match {
    case ReturnFound.IFString        => Some(ReturnFound)
    case NoticeToFileIssued.IFString => Some(NoticeToFileIssued)
    case NoReturnFound.IFString      => Some(NoReturnFound)
    case _                           => None
  }
}
