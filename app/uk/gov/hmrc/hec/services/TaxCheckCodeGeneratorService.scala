/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckCode

import scala.util.Random

@ImplementedBy(classOf[TaxCheckCodeGeneratorServiceImpl])
trait TaxCheckCodeGeneratorService {

  def generateTaxCheckCode(): HECTaxCheckCode

}

@Singleton
class TaxCheckCodeGeneratorServiceImpl extends TaxCheckCodeGeneratorService {

  private def randomElement[A](l: List[A]): A = l(Random.nextInt(l.length))

  def generateTaxCheckCode(): HECTaxCheckCode = {
    val code = List.fill(HECTaxCheckCode.validLength)(randomElement(HECTaxCheckCode.validCharacters)).mkString("")
    HECTaxCheckCode(code)
  }

}
