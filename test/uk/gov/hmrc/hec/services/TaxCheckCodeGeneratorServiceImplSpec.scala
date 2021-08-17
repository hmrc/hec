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

package uk.gov.hmrc.hec.services

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.hec.models.HECTaxCheckCode

class TaxCheckCodeGeneratorServiceImplSpec extends AnyWordSpec with Matchers {

  val service = new TaxCheckCodeGeneratorServiceImpl

  "TaxCheckCodeGeneratorServiceImplSpec" must {

    "generate a code which doesn't contain any disallowed characters" in {
      val codes = List.fill(10000)(service.next())

      codes.foreach { case HECTaxCheckCode(code) =>
        withClue(s"For code $code: ") {
          code.length                               shouldBe 9
          code.forall(c => c.isDigit || c.isLetter) shouldBe true
          code.filter(_.isLetter).forall(_.isUpper) shouldBe true
          code shouldNot contain('0')
          code shouldNot contain('1')
          code shouldNot contain('5')
          code shouldNot contain('I')
          code shouldNot contain('O')
          code shouldNot contain('S')
          code shouldNot contain('U')
          code shouldNot contain('V')
          code shouldNot contain('W')
        }
      }

    }

  }

}
