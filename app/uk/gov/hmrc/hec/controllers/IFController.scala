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

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.hec.models.TaxYear
import uk.gov.hmrc.hec.models.ids.{CTUTR, SAUTR}
import uk.gov.hmrc.hec.services.IFService
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.hec.util.Logging.LoggerOps
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IFController @Inject() (
  IFService: IFService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  private def getError[A](optional: Option[A], error: String) = if (optional.isEmpty) Some(error) else None
  private def collateErrors(errors: Seq[Option[String]])      = errors.collect({ case Some(v) => v }).mkString("; ")

  /**
    * Fetch individual user's self-assessment status
    * @param utr Self-assessment UTR
    * @param taxYear Start tax year
    * @return Self-assessment status with UTR and tax year
    */
  def getSAStatus(utr: String, taxYear: String): Action[AnyContent] = Action.async { implicit request =>
    val validSAUTROpt   = SAUTR.fromString(utr)
    val validTaxYearOpt = TaxYear.fromString(taxYear)

    (validSAUTROpt, validTaxYearOpt) match {
      case (Some(utr), Some(year)) =>
        IFService
          .getSAStatus(utr, year)
          .fold(
            { e =>
              logger.warn("Could not fetch SA status", e)
              InternalServerError
            },
            saStatus => Ok(Json.toJson(saStatus))
          )

      case (utr, year) =>
        Future.successful(
          BadRequest(
            collateErrors(
              Seq(
                getError(utr, "Invalid SAUTR"),
                getError(year, "Invalid tax year")
              )
            )
          )
        )
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
    val validCTUTROpt = CTUTR.fromString(utr)

    def parsedDate(dateStr: String): Option[LocalDate] = try Some(LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE))
    catch {
      case _: Exception => None
    }

    val validFromDateOpt = parsedDate(fromDate)
    val validToDateOpt   = parsedDate(toDate)

    (validCTUTROpt, validFromDateOpt, validToDateOpt) match {
      case (Some(utr), Some(from), Some(to)) =>
        IFService
          .getCTStatus(utr, from, to)
          .fold(
            { e =>
              logger.warn("Could not fetch CT status", e)
              InternalServerError
            },
            ctStatus => Ok(Json.toJson(ctStatus))
          )

      case (utr, from, to) =>
        Future.successful(
          BadRequest(
            collateErrors(
              Seq(
                getError(utr, "Invalid CTUTR"),
                getError(from, "Invalid fromDate format"),
                getError(to, "Invalid toDate format")
              )
            )
          )
        )
    }
  }
}
