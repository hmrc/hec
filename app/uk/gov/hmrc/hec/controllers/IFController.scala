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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.hec.models.TaxYear
import uk.gov.hmrc.hec.models.ids.{CTUTR, SAUTR}
import uk.gov.hmrc.hec.services.{IFService, IFServiceImpl}
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.hec.util.Logging.LoggerOps
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated._
import cats.implicits._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IFController @Inject() (
  IFService: IFService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  /**
    * Fetch individual user's self-assessment status
    * @param utr Self-assessment UTR
    * @param taxYear Start tax year
    * @return Self-assessment status with UTR and tax year
    */
  def getSAStatus(utr: String, taxYear: String): Action[AnyContent] = Action.async { implicit request =>
    val sautrValidation   = SAUTR.fromString(utr).toValidNel("Invalid SAUTR")
    val taxYearValidation = TaxYear.fromString(taxYear).toValidNel("Invalid tax year")

    @SuppressWarnings(Array("org.wartremover.warts.Any"))
    val validation = (sautrValidation, taxYearValidation).mapN((_, _))
    validation match {
      case Valid((utr, year)) =>
        IFService
          .getSAStatus(utr, year)
          .fold(
            {
              case IFServiceImpl.DataError(msg)  =>
                logger.warn(msg)
                NotFound
              case IFServiceImpl.BackendError(e) =>
                logger.warn("Could not fetch SA status", e)
                InternalServerError
            },
            saStatus => Ok(Json.toJson(saStatus))
          )

      case Invalid(e) => Future.successful(BadRequest(e.toList.mkString("; ")))
    }
  }

  /**
    * Fetch a company's corporation tax status
    * @param utr The corporation tax UTR
    * @param fromDate The start date of the tax period
    * @param toDate The end date of the tax period
    * @return Corporation tax status & accounting periods
    */
  def getCTStatus(
    utr: String,
    fromDate: String,
    toDate: String
  ): Action[AnyContent] = Action.async { implicit request =>
    def parsedDate(dateStr: String, error: String): Validated[NonEmptyList[String], LocalDate] =
      try LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE).valid
      catch {
        case _: Exception => invalidNel(error)
      }

    val ctutrValidation: ValidatedNel[String, CTUTR]        = CTUTR.fromString(utr).toValidNel("Invalid CTUTR")
    val fromDateValidation: ValidatedNel[String, LocalDate] = parsedDate(fromDate, "Invalid fromDate format")
    val toDateValidation: ValidatedNel[String, LocalDate]   = parsedDate(toDate, "Invalid toDate format")

    @SuppressWarnings(Array("org.wartremover.warts.Any"))
    val validation = (ctutrValidation, fromDateValidation, toDateValidation).mapN((_, _, _))
    validation match {
      case Valid((utr, from, to)) =>
        IFService
          .getCTStatus(utr, from, to)
          .fold(
            {
              case IFServiceImpl.DataError(msg)  =>
                logger.warn(msg)
                NotFound
              case IFServiceImpl.BackendError(e) =>
                logger.warn("Could not fetch CT status", e)
                InternalServerError
            },
            ctStatus => Ok(Json.toJson(ctStatus))
          )

      case Invalid(e) => Future.successful(BadRequest(e.toList.mkString(";")))
    }
  }
}
