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
import uk.gov.hmrc.hec.models.HECTaxCheckData.IndividualHECTaxCheckData
import uk.gov.hmrc.hec.models.SAStatus._
import uk.gov.hmrc.hec.models.{CorrectiveAction, Error, HECTaxCheck, SAStatus, TaxSituation}
import uk.gov.hmrc.hec.models.fileFormat.FileFormat.toFileContent
import uk.gov.hmrc.hec.models.TaxSituation._
import uk.gov.hmrc.hec.models.fileFormat.{EnumFileBody, FileFormat, FileHeader, FileTrailer, HECTaxCheckFileBody}
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
  ): Either[Error, (String, String)] =
    getFileBodyContents(inputType).map(createContent(seqNum, partialFileName, _))

  def licenceTimeTradingEKV(licenceTimeTrading: LicenceTimeTrading): (String, String) = licenceTimeTrading match {
    case ZeroToTwoYears   => ("00", "0 to 2 years")
    case TwoToFourYears   => ("01", "2 to 4 years")
    case FourToEightYears => ("02", "4 to 8 years")
    case EightYearsOrMore => ("03", "More than 8 years")
  }

  private def licenceTypeEKV(licenceType: LicenceType): (String, String) = licenceType match {
    case DriverOfTaxisAndPrivateHires  => ("00", "Driver of taxis and private hires")
    case OperatorOfPrivateHireVehicles => ("01", "Operator of private hire vehicles")
    case ScrapMetalMobileCollector     => ("02", "Scrap metal mobile collector")
    case ScrapMetalDealerSite          => ("03", "Scrap metal dealer site")
  }

  def licenceValidityPeriodEKV(licenceValidityPeriod: LicenceValidityPeriod): (String, String) =
    licenceValidityPeriod match {
      case UpToOneYear    => ("00", "Up to 1 year")
      case UpToTwoYears   => ("01", "Up to 2 years")
      case UpToThreeYears => ("02", "Up to 3 years")
      case UpToFourYears  => ("03", "Up to 4 years")
      case UpToFiveYears  => ("04", "Up to 5 years")
    }

  def correctiveActionEKV(correctiveAction: CorrectiveAction): (String, String) = correctiveAction match {
    case Register => ("00", "Register new SA account")
    case Dormant  => ("01", "Dormant account reactivated")
    case Other    => ("02", "Other corrective action")

  }

  private def createLicenceTimeTradingEnumFileBody =
    LicenceTimeTrading.values.map { values =>
      val keyValue = licenceTimeTradingEKV(values)
      EnumFileBody(recordId = keyValue._1, recordDescription = keyValue._2)
    }.toList

  private def createLicenceTypeEnumFileBody =
    LicenceType.values.map { values =>
      val keyValue = licenceTypeEKV(values)
      EnumFileBody(recordId = keyValue._1, recordDescription = keyValue._2)
    }.toList

  private def createLicenceValidityPeriodFileBody =
    LicenceValidityPeriod.values.map { values =>
      val keyValue = licenceValidityPeriodEKV(values)
      EnumFileBody(recordId = keyValue._1, recordDescription = keyValue._2)
    }.toList

  private def createCorrectiveActionFileBody =
    CorrectiveAction.values.map { values =>
      val keyValue = correctiveActionEKV(values)
      EnumFileBody(recordId = keyValue._1, recordDescription = keyValue._2)
    }.toList

  case class TaxSituationMapping(SA: Option[Char], PAYE: Option[Char], NotChargeable: Option[Char])

  private def taxSituationMapping(taxSituation: TaxSituation) = taxSituation match {
    case PAYE          => TaxSituationMapping(Some('N'), Some('Y'), None)
    case SA            => TaxSituationMapping(Some('Y'), Some('N'), None)
    case SAPAYE        => TaxSituationMapping(Some('Y'), Some('Y'), None)
    case NotChargeable => TaxSituationMapping(None, None, Some('Y'))
  }

  case class SAStatusMapping(returnReceived: Option[Char], noticeToFileIssued: Option[Char])

  private def saStatusMapping(saStatus: Option[SAStatus]) = saStatus match {
    case Some(ReturnFound)        => SAStatusMapping(Some('Y'), None)
    case Some(NoticeToFileIssued) => SAStatusMapping(Some('N'), Some('Y'))
    case Some(NoReturnFound)      => SAStatusMapping(Some('N'), Some('N'))
    case None                     => SAStatusMapping(None, None)
  }

  private def createHecTaxCheckFileBody(hecTaxCheckList: List[HECTaxCheck]): List[EnumFileBody] =
    hecTaxCheckList.map(hecTaxCheck =>
      hecTaxCheck.taxCheckData match {
        case i: IndividualHECTaxCheckData =>
          HECTaxCheckFileBody(
            ggCredID = Some(i.applicantDetails.ggCredId.value),
            nino = Some(i.taxDetails.nino.value),
            firstName = Some(i.applicantDetails.name.firstName),
            lastName = Some(i.applicantDetails.name.lastName),
            dob = Some(i.applicantDetails.dateOfBirth.value.format(DATE_FORMATTER)),
            SAUTR = i.taxDetails.sautr.map(_.value.toInt),
            licenceType = licenceTypeEKV(i.licenceDetails.licenceType)._1,
            licenceValidityPeriod = licenceValidityPeriodEKV(i.licenceDetails.licenceValidityPeriod)._1,
            licenceTimeTrading = licenceTimeTradingEKV(i.licenceDetails.licenceTimeTrading)._1,
            entityType = 'I',
            notChargeable = taxSituationMapping(i.taxDetails.taxSituation).NotChargeable,
            PAYE = taxSituationMapping(i.taxDetails.taxSituation).PAYE,
            SA = taxSituationMapping(i.taxDetails.taxSituation).SA,
            incomeTaxYear = i.taxDetails.saStatusResponse.map(_.taxYear.startYear + 1),
            returnReceived = saStatusMapping(i.taxDetails.saStatusResponse.map(_.status)).returnReceived,
            noticeToFile = saStatusMapping(i.taxDetails.saStatusResponse.map(_.status)).noticeToFileIssued,
            taxComplianceDeclaration = if(saStatusMapping(i.taxDetails.saStatusResponse.map(_.status)).returnReceived == 'Y') Some('Y') else Some('N'),
            correctiveAction =
          )
        case _                            => List.empty
      }
    )

  //Create the  file body contents excluding header and trailer
  private def getFileBodyContents[A](inputType: A): Either[Error, List[EnumFileBody]] =
    inputType match {
      case LicenceType                                   => Right(createLicenceTypeEnumFileBody)
      case LicenceTimeTrading                            => Right(createLicenceTimeTradingEnumFileBody)
      case LicenceValidityPeriod                         => Right(createLicenceValidityPeriodFileBody)
      case CorrectiveAction                              => Right(createCorrectiveActionFileBody)
      case (h: HECTaxCheck) :: (tail: List[HECTaxCheck]) => Right(createHecTaxCheckFileBody(h :: tail))
      case _                                             => Left(Error("Input Type is not valid."))
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
