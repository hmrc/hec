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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FileFormatSpec extends AnyWordSpec with Matchers {

  "FileFormatSpec" must {

    "convert a whole file content into pipe delimited string with new space" in {

      val fileHeader   = FileHeader(fileName = "file1.dat", dateOfExtract = "20210909", timeOfExtract = "154556")
      val fileTrailer  = FileTrailer(fileName = "file1.dat", recordCount = 9999L, inSequenceFlag = 'Y')
      val fileBodyList = List(
        EnumFileBody(recordId = "00", recordDescription = "record1"),
        EnumFileBody(recordId = "01", recordDescription = "record2"),
        EnumFileBody(recordId = "02", recordDescription = "record3")
      )

      val fileFormat = FileFormat(fileHeader, fileBodyList, fileTrailer)
      val result     = FileFormat.toFileContent(fileFormat)
      val expected   =
        s"""|00|file1.dat|HEC|SSA|20210909|154556|000001|001
            |01|00|record1
            |01|01|record2
            |01|02|record3
            |99|file1.dat|9999|Y""".stripMargin

      result shouldBe expected
    }
  }
}
