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

package uk.gov.hmrc.hec.services.scheduleService

import cats.data.EitherT
import cats.implicits._
import com.google.inject.{ImplementedBy, Inject}
import org.joda.time.Duration
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.HECTaxCheck
import uk.gov.hmrc.hec.services.TaxCheckService
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}

import javax.inject.Singleton
import scala.concurrent.Future

@ImplementedBy(classOf[HecTaxCheckExtractionServiceImpl])
trait HecTaxCheckExtractionService {

  def lockAndExtractJob(): Future[Option[Either[models.Error, List[HECTaxCheck]]]]

}

@Singleton
class HecTaxCheckExtractionServiceImpl @Inject() (
  taxCheckService: TaxCheckService,
  mongo: ReactiveMongoComponent,
  config: Configuration
)(implicit
  hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
) extends HecTaxCheckExtractionService
    with Logging {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val generateLock: LockKeeper =
    new LockKeeper {

      override def repo: LockRepository            = LockMongoRepository(mongo.mongoConnector.db)
      override def lockId: String                  = "hecTaxChecks"
      override val forceLockReleaseAfter: Duration =
        Duration.standardMinutes(config.get[Long]("mongo-lock.forceLockReleaseAfter"))
    }

  override def lockAndExtractJob(): Future[Option[Either[models.Error, List[HECTaxCheck]]]] =
    generateLock tryLock processHecData

  private def processHecData()(implicit hc: HeaderCarrier): Future[Either[models.Error, List[HECTaxCheck]]] = {
    val result: EitherT[Future, models.Error, List[HECTaxCheck]] = {
      for {
        hecTaxCheck      <- taxCheckService.getAllTaxCheckCodesByExtractedStatus(false)
        //TODO process to generate fields from extracted data will be called here
        _                 = hecTaxCheck.foreach(h =>
                              logger.info(s" Job submitted to extract hec Tax check code :: ${h.taxCheckCode.value}")
                            )
        updatedHecTaxChek = hecTaxCheck.map(_.copy(isExtracted = true))
        //updating isExtracted to true for the the processed hec tax check codes
        newHecTaxCheck   <- taxCheckService.updateAllHecTaxCheck(updatedHecTaxChek)
      } yield newHecTaxCheck
    }
    result.value
  }
}
