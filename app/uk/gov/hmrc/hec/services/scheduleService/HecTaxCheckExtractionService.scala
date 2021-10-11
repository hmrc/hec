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
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.licence.{LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.{Error, HECTaxCheck}
import uk.gov.hmrc.hec.services.scheduleService.HecTaxCheckExtractionServiceImpl._
import uk.gov.hmrc.hec.services.{FileCreationService, FileStoreService, MongoLockService, TaxCheckService}
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Singleton
import scala.concurrent.Future

@ImplementedBy(classOf[HecTaxCheckExtractionServiceImpl])
trait HecTaxCheckExtractionService {

  def lockAndProcessHecData(): Future[Option[Either[models.Error, List[HECTaxCheck]]]]

}

@Singleton
class HecTaxCheckExtractionServiceImpl @Inject() (
  taxCheckService: TaxCheckService,
  mongoLockService: MongoLockService,
  fileCreationService: FileCreationService,
  fileStoreService: FileStoreService
)(implicit
  hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
) extends HecTaxCheckExtractionService
    with Logging {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val licenceType: FileDetails[LicenceType]                     = FileDetails[LicenceType]("licence-type", "LICENCE_TYPE")
  val licenceTimeTrading: FileDetails[LicenceTimeTrading]       =
    FileDetails[LicenceTimeTrading]("licence-time-trading", "LICENCE_TIME_TRADING")
  val licenceValidityPeriod: FileDetails[LicenceValidityPeriod] =
    FileDetails[LicenceValidityPeriod]("licence-validity-period", "LICENCE_VALIDITY_PERIOD")

  override def lockAndProcessHecData(): Future[Option[Either[models.Error, List[HECTaxCheck]]]] =
    mongoLockService.withLock(processHecData)

  private def processHecData()(implicit hc: HeaderCarrier): Future[Either[models.Error, List[HECTaxCheck]]] = {
    val seqNum = "0001"
    val result: EitherT[Future, models.Error, List[HECTaxCheck]] = {
      for {
        hecTaxCheck      <- taxCheckService.getAllTaxCheckCodesByExtractedStatus(false)
        _                 = hecTaxCheck.foreach(h =>
                              logger.info(s" Job submitted to extract hec Tax check code :: ${h.taxCheckCode.value}")
                            )
        updatedHecTaxChek = hecTaxCheck.map(_.copy(isExtracted = true))
        _                <- createAndStoreFile(LicenceType, seqNum, licenceType.partialFileName, licenceType.dirName)
        _                <-
          createAndStoreFile(LicenceTimeTrading, seqNum, licenceTimeTrading.partialFileName, licenceTimeTrading.dirName)
        _                <- createAndStoreFile(
                              LicenceValidityPeriod,
                              seqNum,
                              licenceValidityPeriod.partialFileName,
                              licenceValidityPeriod.dirName
                            )
        //updating isExtracted to true for the the processed hec tax check codes
        newHecTaxCheck   <- taxCheckService.updateAllHecTaxCheck(updatedHecTaxChek)
      } yield newHecTaxCheck
    }
    result.value
  }

  //Combining the process of creating and Storing file
  //useful when we have to create n number of files for large dataset.
  private def createAndStoreFile[A](
    inputType: A,
    seqNum: String,
    partialFileName: String,
    dirName: String
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] = for {
    fileContent <- EitherT.fromEither[Future](fileCreationService.createFileContent(inputType, seqNum, partialFileName))
    _           <- fileStoreService.storeFile(fileContent._1, fileContent._2, dirName)

  } yield ()

}

object HecTaxCheckExtractionServiceImpl {
  final case class FileDetails[A](dirName: String, partialFileName: String)

}
