/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.hec.actors

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.hec.util.TimeProvider
import java.time.{LocalTime, ZoneId}
import scala.concurrent.duration._

class TimeCalculatorSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockTimeProvider = mock[TimeProvider]

  def mockCurrentTime(zone: ZoneId)(response: LocalTime): Unit =
    (mockTimeProvider
      .currentTime(_: ZoneId))
      .expects(zone)
      .returning(response)

  val timeCalculator = new TimeCalculatorImpl(mockTimeProvider)

  "TimeCalculatorImpl" must {

    val zoneId = ZoneId.of("Europe/London")

    "calculate time between two times correctly if now is midnight" in {
      inSequence {
        mockCurrentTime(zoneId)(LocalTime.MIDNIGHT)
      }
      val expectedTime: FiniteDuration = 13.hours + 24.minutes
      timeCalculator.timeUntil(LocalTime.of(13, 24), zoneId) shouldBe expectedTime

    }

  }

}
