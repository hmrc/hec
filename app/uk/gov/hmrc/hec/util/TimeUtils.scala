/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.hec.util

import java.time.{Clock, LocalDate, LocalTime, ZoneId, ZonedDateTime}

object TimeUtils {

  val clock: Clock = Clock.systemUTC()

  def today(): LocalDate               = LocalDate.now(clock)
  def now(): ZonedDateTime             = ZonedDateTime.now(ZoneId.of("Europe/London"))
  def nowTime(zone: ZoneId): LocalTime = LocalTime.now(zone)

}
