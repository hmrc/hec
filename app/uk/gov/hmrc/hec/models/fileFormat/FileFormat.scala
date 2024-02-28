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

package uk.gov.hmrc.hec.models.fileFormat

final case class FileFormat(header: FileHeader, body: List[FileBody], trailer: FileTrailer)

object FileFormat {

  // convert the whole file content into pipe delimited string with appropriate new line
  def toFileContent(fileFormat: FileFormat): String = {
    val header  = FileHeader.toRowString(fileFormat.header)
    val body    = fileFormat.body.map(_.toRowString).mkString("\n")
    val trailer = FileTrailer.toRowString(fileFormat.trailer)

    // regex to remove the empty lines from the file generated
    s"$header\n$body\n$trailer\n".replaceAll("(?m)^[ \t]*\r?\n", "")
  }
}
