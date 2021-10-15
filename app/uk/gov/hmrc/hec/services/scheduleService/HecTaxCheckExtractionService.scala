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
import uk.gov.hmrc.hec.models.licence.{LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.models.{CorrectiveAction, Error, HECTaxCheck, HECTaxCheckFileBodyList}
import uk.gov.hmrc.hec.services.scheduleService.HecTaxCheckExtractionServiceImpl._
import uk.gov.hmrc.hec.services.{FileCreationService, FileStoreService, MongoLockService, TaxCheckService}
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Singleton
import scala.annotation.tailrec
import scala.concurrent.Future

@ImplementedBy(classOf[HecTaxCheckExtractionServiceImpl])
trait HecTaxCheckExtractionService {

  def lockAndProcessHecData(): Future[Option[Either[models.Error, Unit]]]

}

@Singleton
class HecTaxCheckExtractionServiceImpl @Inject() (
  taxCheckService: TaxCheckService,
  mongoLockService: MongoLockService,
  fileCreationService: FileCreationService,
  fileStoreService: FileStoreService,
  config: Configuration
)(implicit
  hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
) extends HecTaxCheckExtractionService
    with Logging {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val count: Int                 = config.get[Int]("hec-tax-heck-file.default-size")
  val hec: String                = "HEC"

  val licenceType: FileDetails[LicenceType]                     = FileDetails[LicenceType]("licence-type", s"${hec}_LICENCE_TYPE")
  val licenceTimeTrading: FileDetails[LicenceTimeTrading]       =
    FileDetails[LicenceTimeTrading]("licence-time-trading", s"${hec}_LICENCE_TIME_TRADING")
  val licenceValidityPeriod: FileDetails[LicenceValidityPeriod] =
    FileDetails[LicenceValidityPeriod]("licence-validity-period", s"${hec}_LICENCE_VALIDITY_PERIOD")

  val correctiveAction: FileDetails[CorrectiveAction] =
    FileDetails[CorrectiveAction]("corrective-action", s"${hec}_CORRECTIVE_ACTION")

  val hecData: FileDetails[List[HECTaxCheck]] = FileDetails[List[HECTaxCheck]]("tax-checks", s"$hec")

  override def lockAndProcessHecData(): Future[Option[Either[models.Error, Unit]]] =
    mongoLockService.withLock(processHecData)

  private def processHecData()(implicit hc: HeaderCarrier): Future[Either[models.Error, Unit]] = {
    val seqNum = "0001"
    val result: EitherT[Future, models.Error, Unit] = {
      for {
        _           <- createAndStoreFile(LicenceType, seqNum, licenceType.partialFileName, licenceType.dirName, false)
        _           <-
          createAndStoreFile(
            LicenceTimeTrading,
            seqNum,
            licenceTimeTrading.partialFileName,
            licenceTimeTrading.dirName,
            false
          )
        _           <- createAndStoreFile(
                         LicenceValidityPeriod,
                         seqNum,
                         licenceValidityPeriod.partialFileName,
                         licenceValidityPeriod.dirName,
                         false
                       )
        _           <- createAndStoreFile(
                         CorrectiveAction,
                         seqNum,
                         correctiveAction.partialFileName,
                         correctiveAction.dirName,
                         false
                       )
        hecTaxCheck <- taxCheckService.getAllTaxCheckCodesByExtractedStatus(false)
        _           <- createHecFile(HECTaxCheckFileBodyList(hecTaxCheck), count, hecData.partialFileName, hecData.dirName)
        //updating isExtracted to true for the the processed hec tax check codes
        // newHecTaxCheck   <- taxCheckService.updateAllHecTaxCheck(updatedHecTaxChek)
      } yield ()
    }
    result.value
  }

  private def createHecFile(
    hecTaxCheckList: HECTaxCheckFileBodyList,
    count: Int,
    partialFileName: String,
    dirname: String
  ) = {

    @tailrec
    def loop(
      hecTaxCheckList: List[HECTaxCheck],
      count: Int,
      seqNumInt: Int,
      partialFileName: String,
      dirname: String
    ): EitherT[Future, Error, Unit] = {
      val hecList       = hecTaxCheckList.take(count)
      val remainingList = hecTaxCheckList.drop(count)
      val isRemaining   = remainingList.toIterator.hasNext
      if (hecList.size =!= 0) {
        val _ = createAndStoreFile(
          HECTaxCheckFileBodyList(hecList),
          seqNumInt.toString.takeRight(4),
          partialFileName,
          dirname,
          isRemaining
        )
        loop(remainingList, count, seqNumInt + 1, partialFileName, dirname)
      } else { EitherT.fromEither[Future](Right(())) }
    }
    loop(hecTaxCheckList.list, count, 10001, partialFileName, dirname)

  }

  //Combining the process of creating and Storing file
  //useful when we have to create n number of files for large dataset.
  private def createAndStoreFile[A](
    inputType: A,
    seqNum: String,
    partialFileName: String,
    dirName: String,
    isRemaining: Boolean
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] = for {
    fileContent <-
      EitherT.fromEither[Future](fileCreationService.createFileContent(inputType, seqNum, partialFileName, isRemaining))
    _           <- fileStoreService.storeFile(fileContent._1, fileContent._2, dirName)

  } yield ()

}

object HecTaxCheckExtractionServiceImpl {
  final case class FileDetails[A](dirName: String, partialFileName: String)

}
