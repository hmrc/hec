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
import cats.data.EitherT
import cats.implicits._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import com.typesafe.config.Config
import configs.syntax._
import uk.gov.hmrc.hec.actors.TimeCalculatorImpl
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.HECTaxCheck
import uk.gov.hmrc.hec.services.HECTaxCheckExtractionService.OnCompleteHandler
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, LocalTime, ZoneId}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@Singleton
class HECTaxCheckExtractionService @Inject() (
  actorSystem: ActorSystem,
  taxCheckService: TaxCheckService,
  lockKeeperService: LockKeeperService,
  onCompleteHandler: OnCompleteHandler,
  config: Config,
  hECTaxCheckExtractionContext: HECTaxCheckExtractionContext
)(implicit
  executionContext: HECTaxCheckExtractionContext,
  hc: HeaderCarrier
) extends Logging {

  val jobConfig: Config = config.get[Config]("hec-file-extraction-details").value
  private val timeCalculator = {
    val clock = Clock.system(ZoneId.of(jobConfig.getString("extraction-timezone")))
    new TimeCalculatorImpl(clock)
  }

  val jobStartTime: LocalTime              = LocalTime.parse(jobConfig.get[String]("extraction-time").value)
  private val initialDelay: FiniteDuration = timeCalculator.timeUntil(jobStartTime)
  private val interval: FiniteDuration     = jobConfig.get[FiniteDuration]("interval").value

  val lockAndRunScheduleJob: Unit = {
    val lockedJob: Future[Unit] = lockKeeperService.generateLockFor("HecTaxCheck") tryLock {
      scheduleExtractionJob
    } map {
      case Some(_) =>
        logger.info(s"Job ran successfully")
      case _       =>
        logger.info(s"Job did not run because repository was locked by another instance of the scheduler.")
    }
    lockedJob.onComplete(_ => onCompleteHandler.onComplete())

  }

  val _: Cancellable =
    actorSystem.scheduler.scheduleWithFixedDelay(initialDelay, interval)(() => lockAndRunScheduleJob)(
      hECTaxCheckExtractionContext
    )

  private def updateHecTaxCheckStatus(list: List[HECTaxCheck])(implicit
    hc: HeaderCarrier
  ): EitherT[Future, models.Error, List[HECTaxCheck]] =
    list.traverse[EitherT[Future, models.Error, *], HECTaxCheck](taxCheckService.updateTaxCheck)

  def scheduleExtractionJob()(implicit hc: HeaderCarrier): Future[Either[models.Error, List[HECTaxCheck]]] = {
    val result: EitherT[Future, models.Error, List[HECTaxCheck]] = for {
      hecTaxCheck    <- taxCheckService.getAllTaxCheckCodesByStatus(false)
      _               = hecTaxCheck.foreach(h =>
                          logger.info(s" Job submitted to extract hec Tax check code :: ${h.taxCheckCode.value}")
                        )
      newHecTaxCheck <- updateHecTaxCheckStatus(hecTaxCheck)
    } yield newHecTaxCheck
    result.value
  }

}

object HECTaxCheckExtractionService {

  @ImplementedBy(classOf[DefaultOnCompleteHandler])
  trait OnCompleteHandler {
    def onComplete(): Unit
  }

  @Singleton
  class DefaultOnCompleteHandler extends OnCompleteHandler {
    override def onComplete(): Unit = ()
  }

}
