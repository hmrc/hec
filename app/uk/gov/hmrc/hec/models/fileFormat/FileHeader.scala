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

package uk.gov.hmrc.hec.models.fileFormat

final case class FileHeader(
  recordType: String = "00",
  fileName: String,
  sourceSystem: String = "HEC",
  targetSystem: String = "SSA",
  dateOfExtract: String,
  timeOfExtract: String,
  sequenceNumber: String = "000001",
  metaDataVersion: String = "001"
) extends Product
    with Serializable

object FileHeader {
  def toRowString(header: FileHeader): String = header.productIterator.mkString("|")

}
