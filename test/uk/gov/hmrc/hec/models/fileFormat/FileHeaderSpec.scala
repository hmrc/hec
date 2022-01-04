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
import uk.gov.hmrc.hec.models.fileFormat.FileHeader.toRowString

class FileHeaderSpec extends AnyWordSpec with Matchers {

  "FileHeaderSpec" must {

    "convert a file header into string with pipe delimiter" in {
      val fileHeader     = FileHeader(fileName = "file1.dat", dateOfExtract = "20210909", timeOfExtract = "154556")
      val result         = toRowString(fileHeader)
      val expectedResult = "00|file1.dat|HEC|SSA|20210909|154556|000001|001"
      result shouldBe expectedResult
    }

  }

}
