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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FileTrailerSpec extends AnyWordSpec with Matchers {

  "FileTrailerSpec" must {

    "convert a file trailer into string with pipe delimiter" in {
      val fileTrailer = FileTrailer(fileName = "file1.dat", recordCount = 9999L, inSequenceFlag = 'Y')
      val result      = FileTrailer.toRowString(fileTrailer)
      val expected    = s"99|file1.dat|9999|Y"
      result shouldBe expected
    }

  }
}
