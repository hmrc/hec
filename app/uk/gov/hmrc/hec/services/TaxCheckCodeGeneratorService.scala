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

import com.google.inject.{ImplementedBy, Singleton}
import uk.gov.hmrc.hec.models.HECTaxCheckCode

import scala.util.Random

@ImplementedBy(classOf[TaxCheckCodeGeneratorServiceImpl])
trait TaxCheckCodeGeneratorService {

  def generateTaxCheckCode(): HECTaxCheckCode

}

@Singleton
class TaxCheckCodeGeneratorServiceImpl extends TaxCheckCodeGeneratorService {

  private def randomElement[A](l: List[A]): A = l(Random.nextInt(l.length))

  private val allowedChars: List[Char] = {
    val allowedLetters = ('A' to 'Z').toList.diff(List('I', 'O', 'S', 'U', 'V', 'W'))
    val allowedDigits  = ('0' to '9').toList.diff(List('0', '1', '5'))
    allowedLetters ::: allowedDigits
  }

  def generateTaxCheckCode(): HECTaxCheckCode = {
    val code = List.fill(9)(randomElement(allowedChars.toList)).mkString("")
    HECTaxCheckCode(code)
  }

}
