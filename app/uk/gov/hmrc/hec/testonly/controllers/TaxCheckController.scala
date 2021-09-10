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

package uk.gov.hmrc.hec.testonly.controllers

import cats.instances.future._
import cats.instances.int._
import cats.syntax.eq._
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.hec.models.HECTaxCheckCode
import uk.gov.hmrc.hec.testonly.services.TaxCheckService
import uk.gov.hmrc.hec.testonly.models.SaveTaxCheckRequest
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.hec.util.Logging._
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
    Json.fromJson[SaveTaxCheckRequest](request.body) match {
      case JsSuccess(saveTaxCheckRequest, _) =>
        taxCheckService
          .saveTaxCheck(saveTaxCheckRequest)
          .fold(
            { e =>
              logger.warn("Could not store tax check", e)
              InternalServerError
            },
            _ => Created
          )

      case JsError(e) =>
        Future.successful(BadRequest(s"Could not parse JSON: $e"))

    }

  }

  def getTaxCheck(taxCheckCode: String): Action[AnyContent] = Action.async { implicit request =>
    toTaxCheckCode(taxCheckCode) match {
      case Some(code) =>
        taxCheckService
          .getTaxCheck(code)
          .fold(
            { e =>
              logger.warn(s"Could not get tax check for tax check code $taxCheckCode", e)
              InternalServerError
            },
            _.fold(NotFound("No tax check found"))(taxCheck => Ok(Json.toJson(taxCheck)))
          )

      case None =>
        Future.successful(BadRequest("Invalid tax check code"))
    }
  }

  def deleteTaxCheck(taxCheckCode: String): Action[AnyContent] = Action.async { implicit request =>
    toTaxCheckCode(taxCheckCode) match {
      case Some(code) =>
        taxCheckService
          .deleteTaxCheck(code)
          .fold(
            { e =>
              logger.warn(s"Could not delete tax check for tax check code $taxCheckCode", e)
              InternalServerError
            },
            _ => Ok
          )

      case None =>
        Future.successful(BadRequest("Invalid tax check code"))

    }

  }

  val deleteAllTaxChecks: Action[AnyContent] = Action.async { implicit request =>
    taxCheckService
      .deleteAllTaxCheck()
      .fold(
        { e =>
          logger.warn(s"Could not delete all tax checks", e)
          InternalServerError
        },
        _ => Ok
      )
  }

  private def toTaxCheckCode(s: String): Option[HECTaxCheckCode] =
    if (s.length === HECTaxCheckCode.validLength && s.forall(HECTaxCheckCode.validCharacters.contains(_)))
      Some(HECTaxCheckCode(s))
    else
      None

}
