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

package uk.gov.hmrc.hec.services.scheduleService

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.hec.actors.TimeCalculator
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.SchedulerProvider
import uk.gov.hmrc.hec.services.ResourceLockedException
import uk.gov.hmrc.hec.util.Logging

import java.time.{LocalTime, ZoneId}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

@Singleton
class HECTaxCheckExtractionService @Inject() (
  schedulerProvider: SchedulerProvider,
  timeCalculator: TimeCalculator,
  hecTaxCheckScheduleService: HecTaxCheckScheduleService,
  config: Configuration
)(implicit
  hECTaxCheckExtractionContext: HECTaxCheckExtractionContext
) extends Logging {

  val extractionTimeZone: ZoneId = ZoneId.of(config.get[String]("hec-file-extraction-details.extraction-timezone"))
  val jobStartTime: LocalTime    = LocalTime.parse(config.get[String]("hec-file-extraction-details.extraction-time"))

  private def timeUntilNextJob(): FiniteDuration = timeCalculator.timeUntil(jobStartTime, extractionTimeZone)

  def scheduleNextJob(): Unit = {
    val _ = schedulerProvider.scheduler.scheduleOnce(timeUntilNextJob())(runScheduledJob)(hECTaxCheckExtractionContext)
  }

  def start(): Unit = scheduleNextJob()
  //start()

  //Run the job to put a mongo lock and perform fetch and update
  //Once that is done, call the scheduleNextJob() again to schedule the next job
  // as per the extraction time in conf
  def runScheduledJob(): Unit =
    hecTaxCheckScheduleService.lockAndExtractJob().onComplete { result =>
      result match {
        case Success(mayBeValue) =>
          mayBeValue match {
            case Some(value) =>
              value match {
                case Left(error: models.Error) => logger.info(s"Job did not run because of the error :: $error.")
                case Right(list)               => logger.info(s"Job ran successfully for ${list.size} tax checks")

              }
            case None        => logger.info(s"Job failed as lock can't be obtained.")
          }

        case Failure(ex) =>
          new ResourceLockedException
          logger.info(s"Job failed with exception ${ex.getMessage}.")
      }

      scheduleNextJob()
    }

}
