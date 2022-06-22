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

package uk.gov.hmrc.hec.services

import cats.implicits.catsSyntaxOptionId
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject}
import play.api.Configuration
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.fileFormat.FileFormat.toFileContent
import uk.gov.hmrc.hec.models.fileFormat.HECTaxCheckFileBody.FeatureEnabledField
import uk.gov.hmrc.hec.models.fileFormat._
import uk.gov.hmrc.hec.models.hecTaxCheck.CorrectiveAction._
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckData.{CompanyHECTaxCheckData, IndividualHECTaxCheckData}
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheckSource.Digital
import uk.gov.hmrc.hec.models.hecTaxCheck.TaxSituation._
import uk.gov.hmrc.hec.models.hecTaxCheck._
import uk.gov.hmrc.hec.models.hecTaxCheck.company.CTAccountingPeriod.{CTAccountingPeriodDigital, CTAccountingPeriodStride}
import uk.gov.hmrc.hec.models.hecTaxCheck.company.{CTAccountingPeriod, CTStatus}
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.SAStatus
import uk.gov.hmrc.hec.models.hecTaxCheck.individual.SAStatus._
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.LicenceTimeTrading.{EightYearsOrMore, FourToEightYears, TwoToFourYears, ZeroToTwoYears}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.LicenceType._
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.LicenceValidityPeriod.{UpToFiveYears, UpToFourYears, UpToOneYear, UpToThreeYears, UpToTwoYears}
import uk.gov.hmrc.hec.models.hecTaxCheck.licence.{LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
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
    partialFileName: String,
    isLastInSequence: Boolean
  ): Either[Error, (String, String)]

}

@Singleton
class FileCreationServiceImpl @Inject() (timeProvider: TimeProvider, config: Configuration)
    extends FileCreationService {

  val emailAddressFieldEnabled: Boolean =
    config.get[Boolean]("hec-file-extraction-details.enable-email-address-field")

  val DATE_FORMATTER: DateTimeFormatter      = DateTimeFormatter.ofPattern("yyyyMMdd")
  val TIME_FORMATTER: DateTimeFormatter      = DateTimeFormatter.ofPattern("HHmmss")
  val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  override def createFileContent[A](
    inputType: A,
    seqNum: String,
    partialFileName: String,
    isLastInSequence: Boolean
  ): Either[Error, (String, String)] =
    getFileBodyContents(inputType).map { fileBody =>
      createContent(seqNum, partialFileName, fileBody, isLastInSequence)
    }

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
    case BookingOffice                 => ("04", "Booking office")
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
    case RegisterNewSAAccount      => ("00", "Register new SA account")
    case DormantAccountReactivated => ("01", "Dormant account reactivated")
    case Other                     => ("02", "Other corrective action")

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
  case class CTStatusMapping(
    returnReceived: Option[Char],
    noticeToFileIssued: Option[Char],
    accountingPeriod: Option[Char]
  )

  private def saStatusMapping(saStatus: Option[SAStatus]) = saStatus match {
    case Some(ReturnFound)        => SAStatusMapping(Some('Y'), None)
    case Some(NoticeToFileIssued) => SAStatusMapping(Some('N'), Some('Y'))
    case Some(NoReturnFound)      => SAStatusMapping(Some('N'), Some('N'))
    case None                     => SAStatusMapping(None, None)
  }

  private def ctStatusMappingIfAccountingPeriodExists(
    ctStatus: Option[CTStatus],
    chargeableForCT: Option[YesNoAnswer]
  ) =
    (ctStatus, chargeableForCT) match {

      case (_, Some(YesNoAnswer.No))              => CTStatusMapping(None, None, Some('Y'))
      case (Some(CTStatus.ReturnFound), _)        => CTStatusMapping(Some('Y'), None, Some('Y'))
      case (Some(CTStatus.NoticeToFileIssued), _) => CTStatusMapping(Some('N'), Some('Y'), Some('Y'))
      case (Some(CTStatus.NoReturnFound), _)      => CTStatusMapping(Some('N'), Some('N'), Some('Y'))
      case _                                      => CTStatusMapping(None, None, Some('Y'))

    }

  private def getNotChargeableInfo(ctChargeableOpt: Option[YesNoAnswer]): Option[Char] = ctChargeableOpt match {
    case Some(YesNoAnswer.Yes) => Some('N')
    case Some(YesNoAnswer.No)  => Some('Y')
    case None                  => None
  }

  private def yesNoAnswerMap(yesNoAnswer: Option[YesNoAnswer]): Option[Char] = yesNoAnswer match {
    case Some(YesNoAnswer.Yes) => Some('Y')
    case Some(YesNoAnswer.No)  => Some('N')
    case None                  => None
  }

  private def getCTStatusMap(accountingPeriod: Option[CTAccountingPeriod], chargeableForCT: Option[YesNoAnswer]) =
    accountingPeriod match {
      case Some(d: CTAccountingPeriodDigital) =>
        ctStatusMappingIfAccountingPeriodExists(d.ctStatus.some, chargeableForCT)
      case Some(s: CTAccountingPeriodStride)  => ctStatusMappingIfAccountingPeriodExists(s.ctStatus, chargeableForCT)
      case _                                  => CTStatusMapping(None, None, Some('N'))
    }

  private def createHecTaxCheckFileBody(hecTaxCheckList: List[HECTaxCheck]): List[HECTaxCheckFileBody] =
    hecTaxCheckList.map { hecTaxCheck =>
      val taxCheckData             = hecTaxCheck.taxCheckData
      val licenceType              = licenceTypeEKV(taxCheckData.licenceDetails.licenceType)._1
      val licenceValidityPeriod    = licenceValidityPeriodEKV(taxCheckData.licenceDetails.licenceValidityPeriod)._1
      val licenceTimeTrading       = licenceTimeTradingEKV(taxCheckData.licenceDetails.licenceTimeTrading)._1
      val taxCheckStartDateTime    =
        taxCheckData.taxCheckStartDateTime.withZoneSameInstant(ZoneId.of("GMT")).format(DATE_TIME_FORMATTER)
      val taxCheckCompleteDateTime =
        hecTaxCheck.createDate.withZoneSameInstant(ZoneId.of("GMT")).format(DATE_TIME_FORMATTER)
      val taxCheckCode             = hecTaxCheck.taxCheckCode.value
      val taxCheckExpiryDate       = hecTaxCheck.expiresAfter.format(DATE_FORMATTER)
      val onlineApplication        = if (taxCheckData.source === Digital) 'Y' else 'N'
      val emailAddress             =
        FeatureEnabledField(hecTaxCheck.latestTaxCheckEmailSentTo.map(_.value), emailAddressFieldEnabled)

      taxCheckData match {
        case i: IndividualHECTaxCheckData =>
          val taxSituationMap = taxSituationMapping(i.taxDetails.taxSituation)
          val saStatusMap     = saStatusMapping(i.taxDetails.saStatusResponse.map(_.status))
          HECTaxCheckFileBody(
            ggCredID = i.applicantDetails.ggCredId.map(_.value),
            nino = Some(i.taxDetails.nino.value),
            firstName = Some(i.applicantDetails.name.firstName),
            lastName = Some(i.applicantDetails.name.lastName),
            dob = Some(i.applicantDetails.dateOfBirth.value.format(DATE_FORMATTER)),
            SAUTR = i.taxDetails.sautr.map(_.value),
            licenceType = licenceType,
            licenceValidityPeriod = licenceValidityPeriod,
            licenceTimeTrading = licenceTimeTrading,
            entityType = 'I',
            notChargeable = taxSituationMap.NotChargeable,
            PAYE = taxSituationMap.PAYE,
            SA = taxSituationMap.SA,
            incomeTaxYear = (i.taxDetails.relevantIncomeTaxYear.startYear + 1).some,
            returnReceived = saStatusMap.returnReceived,
            noticeToFile = saStatusMap.noticeToFileIssued,
            taxComplianceDeclaration = yesNoAnswerMap(i.taxDetails.saIncomeDeclared),
            correctiveAction = i.taxDetails.correctiveAction.map(ca => correctiveActionEKV(ca)._1),
            customerDeclaration = 'Y',
            taxCheckStartDateTime = taxCheckStartDateTime,
            taxCheckCompleteDateTime = taxCheckCompleteDateTime,
            taxCheckCode = taxCheckCode,
            taxCheckExpiryDate = taxCheckExpiryDate,
            onlineApplication = onlineApplication,
            emailAddress = emailAddress
          )

        case c: CompanyHECTaxCheckData =>
          val accountingPeriod                     = c.taxDetails.ctStatus.latestAccountingPeriod
          val chargeableForCT                      = c.taxDetails.chargeableForCT
          val ctStatusMap: Option[CTStatusMapping] = getCTStatusMap(accountingPeriod, chargeableForCT).some
          HECTaxCheckFileBody(
            ggCredID = c.applicantDetails.ggCredId.map(_.value),
            CTUTR = Some(c.taxDetails.hmrcCTUTR.value),
            crn = Some(c.applicantDetails.crn.value),
            companyName = Some(c.applicantDetails.companyName.name),
            licenceType = licenceType,
            licenceValidityPeriod = licenceValidityPeriod,
            licenceTimeTrading = licenceTimeTrading,
            entityType = 'C',
            notChargeable = getNotChargeableInfo(c.taxDetails.chargeableForCT),
            hasAccountingPeriod = ctStatusMap.flatMap(_.accountingPeriod),
            accountingPeriodStartDate = accountingPeriod.flatMap(
              _.fold(_.startDate.format(DATE_FORMATTER).some, _ => None)
            ),
            accountingPeriodEndDate = accountingPeriod.map(_.endDate.format(DATE_FORMATTER)),
            recentlyStartedTrading = yesNoAnswerMap(c.taxDetails.recentlyStaredTrading),
            returnReceived = ctStatusMap.flatMap(_.returnReceived),
            noticeToFile = ctStatusMap.flatMap(_.noticeToFileIssued),
            taxComplianceDeclaration = yesNoAnswerMap(c.taxDetails.ctIncomeDeclared),
            correctiveAction = c.taxDetails.correctiveAction.map(ca => correctiveActionEKV(ca)._1),
            customerDeclaration = 'Y',
            taxCheckStartDateTime = taxCheckStartDateTime,
            taxCheckCompleteDateTime = taxCheckCompleteDateTime,
            taxCheckCode = taxCheckCode,
            taxCheckExpiryDate = taxCheckExpiryDate,
            onlineApplication = onlineApplication,
            emailAddress = emailAddress
          )
      }
    }
  //Create the  file body contents excluding header and trailer
  private def getFileBodyContents[A](inputType: A): Either[Error, List[FileBody]]                      =
    inputType match {
      case LicenceType                   => Right(createLicenceTypeEnumFileBody)
      case LicenceTimeTrading            => Right(createLicenceTimeTradingEnumFileBody)
      case LicenceValidityPeriod         => Right(createLicenceValidityPeriodFileBody)
      case CorrectiveAction              => Right(createCorrectiveActionFileBody)
      case HECTaxCheckFileBodyList(list) => Right(createHecTaxCheckFileBody(list))
      case _                             => Left(Error("Input Type is not valid."))
    }

  //Create the  full file content including header and trailer
  private def createContent(
    seqNum: String,
    partialFileName: String,
    fileBody: List[FileBody],
    isLastInSequence: Boolean
  ) = {
    val extractDate = timeProvider.currentDate.format(DATE_FORMATTER)
    val fileName    = s"HEC_SSA_${seqNum}_${extractDate}_$partialFileName.dat"

    val fileHeader  = FileHeader(
      fileName = fileName,
      dateOfExtract = extractDate,
      timeOfExtract = timeProvider.currentTime(ZoneId.of("GMT")).format(TIME_FORMATTER),
      sequenceNumber = s"00$seqNum"
    )
    val fileTrailer =
      FileTrailer(
        fileName = fileName,
        recordCount = (2L + fileBody.size.toLong),
        inSequenceFlag = if (isLastInSequence) 'Y' else 'N'
      )
    (toFileContent(FileFormat(fileHeader, fileBody, fileTrailer)), fileName)

  }

}
