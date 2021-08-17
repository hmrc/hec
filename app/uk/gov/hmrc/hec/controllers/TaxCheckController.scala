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

package uk.gov.hmrc.hec.controllers

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.hec.models.HECTaxCheckData
import uk.gov.hmrc.hec.services.TaxCheckService
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.hec.util.Logging.LoggerOps
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxCheckController @Inject() (
  taxCheckService: TaxCheckService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  val saveTaxCheck: Action[JsValue] = Action(parse.json).async { implicit request =>
    Json.fromJson[HECTaxCheckData](request.body) match {
      case JsSuccess(taxCheckData, _) =>
        taxCheckService
          .saveTaxCheck(taxCheckData)
          .fold(
            { e =>
              logger.warn("Could not store tax check", e)
              InternalServerError
            },
            taxCheck => Created(Json.toJson(taxCheck))
          )

      case JsError(_) =>
        logger.warn("Could not parse JSON")
        Future.successful(BadRequest)

    }

  }

}
