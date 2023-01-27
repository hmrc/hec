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

package uk.gov.hmrc.hec.models

import play.api.libs.json.{JsObject, Json, OWrites}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheck
import uk.gov.hmrc.hec.util.JsObjectUtils._

sealed trait AuditEvent {

  val auditType: String

  val transactionName: String

}

object AuditEvent {

  final case class TaxCheckSuccess(
    taxCheck: HECTaxCheck,
    operatorDetails: Option[StrideOperatorDetails]
  ) extends AuditEvent {
    override val auditType: String = "TaxCheckSuccess"

    override val transactionName: String = "tax-check-success"

  }

  object TaxCheckSuccess {

    implicit val writes: OWrites[TaxCheckSuccess] = {
      def mapFieldName(s: String): String = s match {
        case "crn" => "companyRegistrationNumber"
        case _     => s
      }

      OWrites { t =>
        val taxCheckJson        = {
          val json = Json.toJsObject(t.taxCheck) - "isExtracted" - "fileCorrelationId"
          json.mapJsonFieldNames(mapFieldName)
        }
        val operatorDetailsJson =
          t.operatorDetails.fold(JsObject.empty)(s => JsObject(Map("operatorDetails" -> Json.toJsObject(s))))
        taxCheckJson ++ operatorDetailsJson
      }
    }
  }

}
