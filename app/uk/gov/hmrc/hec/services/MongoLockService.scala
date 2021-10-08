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

import com.google.inject.{ImplementedBy, Inject}
import play.api.Configuration
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.HECTaxCheck
import uk.gov.hmrc.hec.services.scheduleService.HECTaxCheckExtractionContext
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

import javax.inject.Singleton
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@ImplementedBy(classOf[MongoLockServiceImpl])
trait MongoLockService {

  def withLock(data: => Future[Either[models.Error, List[HECTaxCheck]]])(implicit
    hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
  ): Future[Option[Either[models.Error, List[HECTaxCheck]]]]

}

@Singleton
class MongoLockServiceImpl @Inject() (mongoLockRepository: MongoLockRepository, config: Configuration)
    extends MongoLockService {

  val lockService: LockService = LockService(
    mongoLockRepository,
    lockId = "hecTaxChecks",
    ttl = config.get[FiniteDuration]("mongo-lock.force-lock-release-after")
  )

  override def withLock(data: => Future[Either[models.Error, List[HECTaxCheck]]])(implicit
    hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
  ): Future[Option[Either[models.Error, List[HECTaxCheck]]]] =
    lockService.withLock(data)
}
