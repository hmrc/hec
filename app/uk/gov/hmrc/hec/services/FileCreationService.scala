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
import uk.gov.hmrc.hec.models.CorrectiveAction._
import uk.gov.hmrc.hec.models.{CorrectiveAction, Error}
import uk.gov.hmrc.hec.models.fileFormat.FileFormat.toFileContent
import uk.gov.hmrc.hec.models.fileFormat.{EnumFileBody, FileFormat, FileHeader, FileTrailer}
import uk.gov.hmrc.hec.models.licence.LicenceTimeTrading.{EightYearsOrMore, FourToEightYears, TwoToFourYears, ZeroToTwoYears}
import uk.gov.hmrc.hec.models.licence.LicenceType.{DriverOfTaxisAndPrivateHires, OperatorOfPrivateHireVehicles, ScrapMetalDealerSite, ScrapMetalMobileCollector}
import uk.gov.hmrc.hec.models.licence.LicenceValidityPeriod.{UpToFiveYears, UpToFourYears, UpToOneYear, UpToThreeYears, UpToTwoYears}
import uk.gov.hmrc.hec.models.licence.{LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.util.TimeProvider

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Singleton

@ImplementedBy(classOf[FileCreationServiceImpl])
trait FileCreationService {

  //creates the file content and returns it along with full file name in a tuple
  def createFileContent[A](
    inputType: A,
    seqNum: String,
    partialFileName: String
  ): Either[Error, (String, String)]

}

@Singleton
class FileCreationServiceImpl @Inject() (timeProvider: TimeProvider) extends FileCreationService {

  val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
  val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")

  override def createFileContent[A](
    inputType: A,
    seqNum: String,
    partialFileName: String
  ): Either[Error, (String, String)]                                                  =
    getFileBodyContents(inputType).map(createContent(seqNum, partialFileName, _))

  private def createLicenceTimeTradingEnumFileBody = {

    def enumKeysAndValue(licenceTimeTrading: LicenceTimeTrading): (String, String) = licenceTimeTrading match {
      case ZeroToTwoYears   => ("00", "0 to 2 years")
      case TwoToFourYears   => ("01", "2 to 4 years")
      case FourToEightYears => ("02", "4 to 8 years")
      case EightYearsOrMore => ("03", "More than 8 years")
    }
    LicenceTimeTrading.values.map { values =>
      val keyValue = enumKeysAndValue(values)
      EnumFileBody(recordId = keyValue._1, recordDescription = keyValue._2)
    }.toList
  }

  private def createLicenceTypeEnumFileBody = {

    def enumKeysAndValue(licenceType: LicenceType): (String, String) = licenceType match {
      case DriverOfTaxisAndPrivateHires  => ("00", "Driver of taxis and private hires")
      case OperatorOfPrivateHireVehicles => ("01", "Operator of private hire vehicles")
      case ScrapMetalMobileCollector     => ("02", "Scrap metal mobile collector")
      case ScrapMetalDealerSite          => ("03", "Scrap metal dealer site")
    }
    LicenceType.values.map { values =>
      val keyValue = enumKeysAndValue(values)
      EnumFileBody(recordId = keyValue._1, recordDescription = keyValue._2)
    }.toList
  }

  private def createLicenceValidityPeriodFileBody = {

    def enumKeysAndValue(licenceValidityPeriod: LicenceValidityPeriod): (String, String) = licenceValidityPeriod match {
      case UpToOneYear    => ("00", "Up to 1 year")
      case UpToTwoYears   => ("01", "Up to 2 years")
      case UpToThreeYears => ("02", "Up to 3 years")
      case UpToFourYears  => ("03", "Up to 4 years")
      case UpToFiveYears  => ("04", "Up to 5 years")
    }
    LicenceValidityPeriod.values.map { values =>
      val keyValue = enumKeysAndValue(values)
      EnumFileBody(recordId = keyValue._1, recordDescription = keyValue._2)
    }.toList
  }

  private def createCorrectiveActionFileBody = {
    def enumKeysAndValue(correctiveAction: CorrectiveAction): (String, String) = correctiveAction match {
      case Register => ("00", "Register new SA account")
      case Dormant  => ("01", "Dormant account reactivated")
      case Other    => ("02", "Other corrective action")

    }
    CorrectiveAction.values.map { values =>
      val keyValue = enumKeysAndValue(values)
      EnumFileBody(recordId = keyValue._1, recordDescription = keyValue._2)
    }.toList
  }
  //Create the  file body contents excluding header and trailer
  private def getFileBodyContents[A](inputType: A): Either[Error, List[EnumFileBody]] =
    inputType match {
      case LicenceType           => Right(createLicenceTypeEnumFileBody)
      case LicenceTimeTrading    => Right(createLicenceTimeTradingEnumFileBody)
      case LicenceValidityPeriod => Right(createLicenceValidityPeriodFileBody)
      case CorrectiveAction      => Right(createCorrectiveActionFileBody)
      case _                     => Left(Error("Input Type is not valid."))
    }

  //Create the  full file content including header and trailer
  private def createContent(seqNum: String, partialFileName: String, enumFileBody: List[EnumFileBody]) = {
    val extractDate = timeProvider.currentDate.format(DATE_FORMATTER)
    val fileName    = s"HEC_SSA_${seqNum}_${extractDate}_$partialFileName.dat"

    val fileHeader  = FileHeader(
      fileName = fileName,
      dateOfExtract = extractDate,
      timeOfExtract = timeProvider.currentTime(ZoneId.of("GMT")).format(TIME_FORMATTER)
    )
    val fileTrailer =
      FileTrailer(fileName = fileName, recordCount = (2L + enumFileBody.size.toLong), inSequenceFlag = 'Y')
    (toFileContent(FileFormat(fileHeader, enumFileBody, fileTrailer)), fileName)

  }

}
