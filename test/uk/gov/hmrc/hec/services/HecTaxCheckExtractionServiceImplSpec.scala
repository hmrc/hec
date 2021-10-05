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

import akka.actor.{ActorRef, ActorSystem, Cancellable, Scheduler}
import akka.pattern.ask
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import com.miguno.akka.testing.{MockScheduler, VirtualTime}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import uk.gov.hmrc.hec.actors.TimeCalculator
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.{Error, HECTaxCheck, SchedulerProvider}
import uk.gov.hmrc.hec.services.HecTaxCheckExtractionServiceImplSpec.{TestHecTaxCheckScheduleService, TestScheduler, TestSchedulerProvider, TestTimeCalculator}
import uk.gov.hmrc.hec.services.HecTaxCheckExtractionServiceImplSpec.TestHecTaxCheckScheduleService.{RunJobRequest, RunJobResponse}
import uk.gov.hmrc.hec.services.HecTaxCheckExtractionServiceImplSpec.TestTimeCalculator.{TimeUntilRequest, TimeUntilResponse}
import uk.gov.hmrc.hec.services.HecTaxCheckExtractionServiceImplSpec.TestScheduler.JobScheduledOnce
import uk.gov.hmrc.hec.services.scheduleService.{HECTaxCheckExtractionContext, HECTaxCheckScheduleService, HecTaxCheckExtractionService}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.format.DateTimeFormatter
import java.time.{LocalTime, ZoneId}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.{global => globalExecutionContext}

class HecTaxCheckExtractionServiceImplSpec
    extends TestKit(
      ActorSystem(
        "hec-tax-check-extraction-service-impl",
        ConfigFactory
          .defaultApplication()
          .resolve()
          .withValue("akka.test.single-expect-default", ConfigValueFactory.fromAnyRef("3 seconds"))
      )
    )
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  implicit val ec: ExecutionContext = globalExecutionContext

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val testProbe = TestProbe()

  // set up with `TestScheduler` here so that when we can be sure that the job has actually been scheduled
  // (after we receive a `JobScheduledOnce` message) before advancing time
  val virtualTime = new VirtualTime() {
    override val scheduler = new TestScheduler(testProbe.ref, this)
  }

  val testScheduleProvider = new TestSchedulerProvider(virtualTime)

  val testHecTaxCheckScheduleService = new TestHecTaxCheckScheduleService(testProbe.ref)
  val testTimeCalculator             = new TestTimeCalculator(testProbe.ref)

  implicit val hecTaxCheckExtractionContext = new HECTaxCheckExtractionContext(system)

  def config(time: String) = Configuration(
    ConfigFactory.parseString(
      s"""
        |hec-file-extraction-details {
        |   extraction-timezone   = "Europe/London"
        |   extraction-time       = "$time"
        |   interval = 1 day
        |}
        |""".stripMargin
    )
  )

  val jobRunTime: LocalTime = LocalTime.now().withSecond(0).withNano(1000000)
  val timeString: String    = jobRunTime.format(DateTimeFormatter.ofPattern("HH:mm"))
  val configuration         = config(timeString)

  "HECTaxCheckExtractionService" must {

    "schedule a job that runs repeatedly at the set interval with the correct initial delay" in {

      // stick in a future to let the scheduler run on a different thread - if it runs on the same thread
      // a timeout will occur because the scheduler is expecting a `TimeUntilResponse` before continuing
      val _ = Future {
        new HECTaxCheckScheduleService(
          testScheduleProvider,
          testTimeCalculator,
          testHecTaxCheckScheduleService,
          configuration
        )
      }

      // the service should ask how long there is until the next job run time
      testProbe.expectMsg(TimeUntilRequest(jobRunTime, ZoneId.of("Europe/London")))
      testProbe.reply(TimeUntilResponse(1.minute))

      testProbe.expectMsg(JobScheduledOnce(1.minute))

      // advance time to just before the job is scheduled to run and make sure the job hasn't run yet
      virtualTime.advance(1.minute.minus(1.milli))
      testProbe.expectNoMessage()
      // advance time to when the job should be run
      virtualTime.advance(1.milli)
      testProbe.expectMsg(RunJobRequest)
      testProbe.reply(RunJobResponse(Some(Left(Error("")))))

      // the next job should be scheduled after the current job has run. Repeat the above
      testProbe.expectMsg(TimeUntilRequest(jobRunTime, ZoneId.of("Europe/London")))
      testProbe.reply(TimeUntilResponse(24.hours))

      testProbe.expectMsg(JobScheduledOnce(24.hours))

      // no job should be run before the next job is scheduled to run
      virtualTime.advance(24.hours.minus(1.milli))
      testProbe.expectNoMessage()

      // the job should now run after the correct time has elapsed
      virtualTime.advance(1.milli)
      testProbe.expectMsg(RunJobRequest)
      testProbe.reply(RunJobResponse(None))

      // the next job should be scheduled after the current job finishes
      testProbe.expectMsg(TimeUntilRequest(jobRunTime, ZoneId.of("Europe/London")))
      testProbe.reply(TimeUntilResponse(24.hours))
      testProbe.expectMsg(JobScheduledOnce(24.hours))

      // end of test - next job won't trigger since we haven't advanced time
    }

  }

}

object HecTaxCheckExtractionServiceImplSpec {

  implicit val timeout: Timeout = Timeout(5.seconds)

  class TestTimeCalculator(reportTo: ActorRef) extends TimeCalculator {

    override def timeUntil(t: LocalTime, zone: ZoneId): FiniteDuration =
      Await.result(
        (reportTo ? TimeUntilRequest(t, zone)).mapTo[TimeUntilResponse].map(_.timeUntil)(globalExecutionContext),
        10.seconds
      )
  }

  object TestTimeCalculator {

    final case class TimeUntilRequest(t: LocalTime, zone: ZoneId)

    final case class TimeUntilResponse(timeUntil: FiniteDuration)

  }

  class TestSchedulerProvider(virtualTime: VirtualTime) extends SchedulerProvider {
    override val scheduler: Scheduler = virtualTime.scheduler
  }

  class TestHecTaxCheckScheduleService(reportTo: ActorRef) extends HecTaxCheckExtractionService {

    def lockAndExtractJob(): Future[Option[Either[models.Error, List[HECTaxCheck]]]] =
      (reportTo ? RunJobRequest).mapTo[RunJobResponse].map(_.result)(globalExecutionContext)
  }

  object TestHecTaxCheckScheduleService {

    case object RunJobRequest

    final case class RunJobResponse(result: Option[Either[models.Error, List[HECTaxCheck]]])

  }

  class TestScheduler(reportTo: ActorRef, time: VirtualTime) extends MockScheduler(time) {

    override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(implicit ec: ExecutionContext): Cancellable = {
      val job = super.scheduleOnce(delay, runnable)
      reportTo ! JobScheduledOnce(delay)
      job
    }
  }

  object TestScheduler {

    final case class JobScheduledOnce(delay: FiniteDuration)

  }

}
