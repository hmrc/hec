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

package uk.gov.hmrc.hec.util

import com.google.inject.{ImplementedBy, Inject}

import java.time.{LocalDate, LocalTime, ZoneId, ZonedDateTime}
import javax.inject.Singleton

@ImplementedBy(classOf[TimeProviderImpl])
trait TimeProvider {
  def currentDateTime: ZonedDateTime
  def currentDate: LocalDate
  def currentTime(zone: ZoneId): LocalTime
}

@Singleton
class TimeProviderImpl extends TimeProvider {
  @Inject()
  override def currentDateTime: ZonedDateTime       = TimeUtils.now()
  override def currentDate: LocalDate               = TimeUtils.today()
  override def currentTime(zone: ZoneId): LocalTime = TimeUtils.nowTime(zone)
}
