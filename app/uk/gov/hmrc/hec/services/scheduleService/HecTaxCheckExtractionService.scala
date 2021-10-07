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
import play.api.Configuration
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.HECTaxCheck
import uk.gov.hmrc.hec.models.licence.{LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.services.TaxCheckService
import uk.gov.hmrc.hec.util.{FileCreationService, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

import javax.inject.Singleton
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@ImplementedBy(classOf[HecTaxCheckExtractionServiceImpl])
trait HecTaxCheckExtractionService {

  def lockAndExtractJob(): Future[Option[Either[models.Error, List[HECTaxCheck]]]]

}

@Singleton
class HecTaxCheckExtractionServiceImpl @Inject() (
  taxCheckService: TaxCheckService,
  mongoLockRepository: MongoLockRepository,
  fileCreationService: FileCreationService,
  fileStoreService: FileStoreService,
  config: Configuration
)(implicit
  hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
) extends HecTaxCheckExtractionService
    with Logging {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val licenceType           = "licence-type"
  val licenceTimeTrading    = "licence-time-trading"
  val licenceValidityPeriod = "licence-validity-period"

  val lockService: LockService = LockService(
    mongoLockRepository,
    lockId = "hecTaxChecks",
    ttl = config.get[FiniteDuration]("mongo-lock.force-lock-release-after")
  )

  override def lockAndExtractJob(): Future[Option[Either[models.Error, List[HECTaxCheck]]]] =
    lockService.withLock(processHecData)

  private def processHecData()(implicit hc: HeaderCarrier): Future[Either[models.Error, List[HECTaxCheck]]] = {

    val result: EitherT[Future, models.Error, List[HECTaxCheck]] = {
      for {
        hecTaxCheck                                                <- taxCheckService.getAllTaxCheckCodesByExtractedStatus(false)
        _                                                           = hecTaxCheck.foreach(h =>
                                                                        logger.info(s" Job submitted to extract hec Tax check code :: ${h.taxCheckCode.value}")
                                                                      )
        updatedHecTaxChek                                           = hecTaxCheck.map(_.copy(isExtracted = true))
        (licenceTypeFileContent, licenceTypeFileName)               = fileCreationService.createFileContent(LicenceType)
        (licenceTimeTradingFileContent, licenceTimeTradingFileName) =
          fileCreationService.createFileContent(LicenceTimeTrading)
        (licenceValidityFileContent, licenceValidityFileName)       =
          fileCreationService.createFileContent(LicenceValidityPeriod)
        _                                                          <- fileStoreService.storeFile(licenceTypeFileContent, licenceTypeFileName, licenceType)
        _                                                          <-
          fileStoreService.storeFile(licenceTimeTradingFileContent, licenceTimeTradingFileName, licenceTimeTrading)
        _                                                          <- fileStoreService.storeFile(licenceValidityFileContent, licenceValidityFileName, licenceValidityPeriod)

        //updating isExtracted to true for the the processed hec tax check codes
        newHecTaxCheck <- taxCheckService.updateAllHecTaxCheck(updatedHecTaxChek)
      } yield newHecTaxCheck
    }
    result.value
  }

}
