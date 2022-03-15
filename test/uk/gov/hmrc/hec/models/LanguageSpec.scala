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

package uk.gov.hmrc.hec.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsNumber, JsString, JsSuccess, Json}

class LanguageSpec extends AnyWordSpec with Matchers {

  def forEachLanguage[A](f: Language => A) =
    List(Language.English, Language.Welsh).foreach { lang =>
      withClue(s"For language $lang: ") {
        f(lang)
      }
    }

  "handling json writes" must {

    "write a json string using the language code" in {
      forEachLanguage { lang =>
        Json.toJson(lang) shouldBe JsString(lang.code)
      }

    }

  }

  "handling json reads" must {

    "return an error" when {

      "the value is not a string" in {
        JsNumber(1).validate[Language] shouldBe a[JsError]
      }

      "the string value is not a recognised language code" in {
        JsString("XX").validate[Language] shouldBe a[JsError]
      }

    }

    "return the correct language" when {

      "a string value is recognised" in {
        forEachLanguage { lang =>
          JsString(lang.code).validate[Language] shouldBe JsSuccess(lang)
        }

      }

    }

  }

}
