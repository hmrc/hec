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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.fileFormat.FileFormat.toFileContent
import uk.gov.hmrc.hec.models.fileFormat.{EnumFileBody, FileFormat, FileHeader, FileTrailer}
import uk.gov.hmrc.hec.models.licence.{LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.util.TimeProvider

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Singleton
import scala.concurrent.Future

@ImplementedBy(classOf[FileCreationServiceImpl])
trait FileCreationService {

  //creates the file content and returns it along with full file name in a tuple
  def createFileContent[A](
    inputType: A,
    seqNum: String,
    partialFileName: String
  ): EitherT[Future, Error, (String, String)]

}

@Singleton
class FileCreationServiceImpl @Inject() (timeProvider: TimeProvider) extends FileCreationService {

  val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
  val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")

  override def createFileContent[A](
    inputType: A,
    seqNum: String,
    partialFileName: String
  ): EitherT[Future, Error, (String, String)] =
    EitherT(getFileBodyContents(inputType) match {
      case Left(e)             => Future.successful(Left(e))
      case Right(enumFileBody) =>
        Future.successful(Right(createContent(seqNum, partialFileName, enumFileBody)))
    })

  //Create the  file body contents excluding header and trailer
  private def getFileBodyContents[A](inputType: A): Either[Error, List[EnumFileBody]] =
    inputType match {
      case LicenceType           => Right(LicenceType.toEnumFileBody)
      case LicenceTimeTrading    => Right(LicenceTimeTrading.toEnumFileBody)
      case LicenceValidityPeriod => Right(LicenceValidityPeriod.toEnumFileBody)
      case _                     => Left(Error("Input Type is not valid."))
    }

  //Create the  full file content including header and trailer
  private def createContent(seqNum: String, partialFileName: String, enumFileBody: List[EnumFileBody]) = {
    val extractDate = timeProvider.currentDate.format(DATE_FORMATTER)
    val fileName    = s"HEC_SSA_${seqNum}_${extractDate}_$partialFileName.dat"

    val fileHeader  = FileHeader(
      fileName = fileName,
      dateOfExtract = extractDate,
      timeOfExtract = timeProvider.currentTime(ZoneId.of("Europe/London")).format(TIME_FORMATTER)
    )
    val fileTrailer =
      FileTrailer(fileName = fileName, recordCount = (2L + enumFileBody.size.toLong), inSequenceFlag = 'Y')
    (toFileContent(FileFormat(fileHeader, enumFileBody, fileTrailer)), fileName)

  }

}
