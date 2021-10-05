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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.hec.util.TimeProvider

import java.time.{LocalTime, ZoneId}
import scala.concurrent.duration._

@ImplementedBy(classOf[TimeCalculatorImpl])
trait TimeCalculator {

  def timeUntil(t: LocalTime, zone: ZoneId): FiniteDuration

}

@Singleton
class TimeCalculatorImpl @Inject() (timeProvider: TimeProvider) extends TimeCalculator {

  private val twentyFourHoursInSeconds: Long = 24.hours.toNanos

  /**
    * Calculating the time until the time passed in parameters from now.
    * @param t
    * @param zone
    * @return FiniteDuration
    */
  def timeUntil(t: LocalTime, zone: ZoneId): FiniteDuration = {

    val now = timeProvider.currentTime(zone)

    val seconds = {
      val delta = now.until(t, java.time.temporal.ChronoUnit.NANOS)
      if (delta < 0) {
        twentyFourHoursInSeconds + delta
      } else {
        delta
      }
    }

    seconds.nanosecond
  }

}
