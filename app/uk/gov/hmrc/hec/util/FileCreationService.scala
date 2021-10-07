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

package uk.gov.hmrc.hec.util

import com.google.inject.{ImplementedBy, Inject}
import uk.gov.hmrc.hec.models.fileFormat.FileFormat.toFileContent
import uk.gov.hmrc.hec.models.fileFormat.{FileFormat, FileHeader, FileTrailer}
import uk.gov.hmrc.hec.models.licence.{LicenceTimeTrading, LicenceType, LicenceValidityPeriod}

import java.time.ZoneId
import java.time.format.DateTimeFormatter

@ImplementedBy(classOf[FileCreationServiceImpl])
trait FileCreationService {

  def createFileContent[A](inputType: A): (String, String)
}

class FileCreationServiceImpl @Inject() (timeProvider: TimeProvider) extends FileCreationService {

  override def createFileContent[A](inputType: A): (String, String) = {

    val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    val timeFormatter = DateTimeFormatter.ofPattern("HHmmss")

    val extractDate = timeProvider.currentDate.format(dateFormatter)

    val (enumFileBody, partialFileName) = inputType match {
      case LicenceType           => (LicenceType.toEnumFileBody, "LICENCE_TYPE")
      case LicenceTimeTrading    => (LicenceTimeTrading.toEnumFileBody, "LICENCE_TIME_TRADING")
      case LicenceValidityPeriod => (LicenceValidityPeriod.toEnumFileBody, "LICENCE_VALIDITY_PERIOD")
    }

    //Should we store HEC and SSA in config or this hard code is fine?
    val fileName = s"HEC_SSA_0001_${extractDate}_$partialFileName.dat"

    val fileHeader = FileHeader(
      fileName = fileName,
      dateOfExtract = extractDate,
      timeOfExtract = timeProvider.currentTime(ZoneId.of("Europe/London")).format(timeFormatter)
    )

    val fileTrailer =
      FileTrailer(fileName = fileName, recordCount = (2L + enumFileBody.size.toLong), inSequenceFlag = 'Y')

    (toFileContent(FileFormat(fileHeader, enumFileBody, fileTrailer)), fileName)
  }
}
