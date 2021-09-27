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

package uk.gov.hmrc.hec.actors

import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Clock, LocalDate, LocalTime, ZoneId, ZoneOffset}

class TimeCalculatorSpec extends AnyWordSpec with Matchers {

  "TimeCalculatorImpl" must {
    // create a clock fixed at midnight
    val clock: Clock =
      Clock.fixed(
        LocalTime
          .parse("13:24")
          .atDate(LocalDate.ofEpochDay(0L))
          .toInstant(ZoneOffset.UTC),
        ZoneId.of("Z")
      )

    val calculator = new TimeCalculatorImpl(clock)

    "calculate time between two times correctly" in {
      val t1                          = LocalTime.MIDNIGHT
      val timeUntilT1: FiniteDuration = 10.hours + 35.minutes + 60.seconds
      calculator.timeUntil(t1) shouldBe timeUntilT1

      val t2          = LocalTime.of(13, 21, 22)
      val timeUntilT2 = 23.hours + 57.minutes + 22.seconds
      calculator.timeUntil(t2) shouldBe timeUntilT2

    }

  }

}
