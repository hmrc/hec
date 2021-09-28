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

import akka.actor.ActorSystem
import akka.event.NoLogging
import akka.testkit.{ExplicitlyTriggeredScheduler, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import uk.gov.hmrc.hec.actors.TimeCalculator
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.HECTaxCheck
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalTime, ZoneId}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class HecTaxCheckExtractionServiceImplSpec
    extends TestKit(ActorSystem.create("hec-tax-check-extraction-service-impl"))
    with AnyWordSpecLike
    with Matchers
    with MockFactory
    with Eventually
    with BeforeAndAfterAll {

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
  implicit val hc: HeaderCarrier                          = HeaderCarrier()

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  val testScheduler = new ExplicitlyTriggeredScheduler(null, NoLogging, null)

  val mockHecTaxCheckScheduleService        = mock[HecTaxCheckScheduleService]
  val mockTimeCalculator                    = mock[TimeCalculator]
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

  val timeNow = LocalTime.now().withSecond(0).withNano(0)

  val timeString: String = s"${timeNow.getHour}:${timeNow.getMinute}"
  val configuration      = config(timeString)

  def mockScheduledJob()(response: Option[Either[models.Error, List[HECTaxCheck]]]) =
    (mockHecTaxCheckScheduleService.scheduleJob _)
      .expects()
      .returning(Future.successful(response))

  def mockTimeUntil(expectedTargetTime: LocalTime, zonedId: ZoneId)(result: FiniteDuration) =
    (mockTimeCalculator
      .timeUntil(_: LocalTime, _: ZoneId))
      .expects(expectedTargetTime, zonedId)
      .returning(result)

  "HECTaxCheckExtractionService" must {

    "schedule a job that runs repeatedly at the set interval with the correct initial delay" in {
      inSequence {
        mockTimeUntil(timeNow, ZoneId.of("Europe/London"))(1.minute)
        mockScheduledJob()(None)
        mockTimeUntil(timeNow, ZoneId.of("Europe/London"))(1.minute)
      }

      val _ =
        new HECTaxCheckExtractionService(
          testScheduler,
          mockTimeCalculator,
          mockHecTaxCheckScheduleService,
          configuration
        )

      testScheduler.timePasses(1.minute)

      //      testScheduler.timePasses(1.minute)
    }

  }

}
