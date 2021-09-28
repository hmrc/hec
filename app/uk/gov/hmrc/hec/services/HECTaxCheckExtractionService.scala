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

import akka.actor.{ActorSystem, Cancellable}
import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import configs.syntax._
import uk.gov.hmrc.hec.actors.TimeCalculatorImpl
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, LocalTime, ZoneId}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

@Singleton
class HECTaxCheckExtractionService @Inject() (
  actorSystem: ActorSystem,
  hecTaxCheckScheduleService: HecTaxCheckScheduleService,
  config: Config,
  hECTaxCheckExtractionContext: HECTaxCheckExtractionContext
)(implicit
  executionContext: HECTaxCheckExtractionContext
) extends Logging {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val jobConfig: Config          = config.get[Config]("hec-file-extraction-details").value
  private val timeCalculator = {
    val clock = Clock.system(ZoneId.of(jobConfig.getString("extraction-timezone")))
    new TimeCalculatorImpl(clock)
  }

  val jobStartTime: LocalTime              = LocalTime.parse(jobConfig.get[String]("extraction-time").value)
  private val initialDelay: FiniteDuration = timeCalculator.timeUntil(jobStartTime)

  private val interval: FiniteDuration = jobConfig.get[FiniteDuration]("interval").value

  val _: Cancellable =
    actorSystem.scheduler.scheduleWithFixedDelay(initialDelay, interval)(() => lockAndRunScheduledJob)(
      hECTaxCheckExtractionContext
    )

  def lockAndRunScheduledJob(): Unit =
    hecTaxCheckScheduleService.scheduleJob().onComplete {
      case Success(mayBeValue) =>
        mayBeValue match {
          case Some(value) =>
            value match {
              case Left(error: models.Error) => logger.info(s"Job did not run because of the error :: $error.")
              case Right(list)               => logger.info(s"Job ran successfully for ${list.size} tax checks")

            }
          case None        => logger.info(s"Job failed as lock can't be obtained.")
        }
      case Failure(ex)         =>
        new ResourceLockedException
        logger.info(s"Job failed with exception ${ex.getMessage}.")

    }

}

//object HECTaxCheckExtractionService {
//
//  @ImplementedBy(classOf[DefaultOnCompleteHandler])
//  trait OnCompleteHandler {
//    def onComplete(): Unit
//  }
//
//  @Singleton
//  class DefaultOnCompleteHandler extends OnCompleteHandler {
//    override def onComplete(): Unit = ()
//  }
//
//}
