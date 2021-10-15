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

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.licence.{LicenceTimeTrading, LicenceType, LicenceValidityPeriod}
import uk.gov.hmrc.hec.util.TimeProvider

import java.time.{LocalDate, LocalTime, ZoneId}

class FileCreationServiceSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockTimeProvider = mock[TimeProvider]

  val fileCreationService = new FileCreationServiceImpl(mockTimeProvider)

  def mockDateProviderToday(d: LocalDate)               = (mockTimeProvider.currentDate _).expects().returning(d)
  def mockTimeProviderNow(d: LocalTime, zoneId: ZoneId) =
    (mockTimeProvider.currentTime(_: ZoneId)).expects(zoneId).returning(d)

  val zoneId = ZoneId.of("Europe/London")

  "FileCreationServiceSpec" must {

    "create file content" when {

      "content Type is licence Type" in {

        inSequence {
          mockDateProviderToday(LocalDate.of(2021, 10, 10))
          mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
        }
        val result: Either[Error, (String, String)] =
          fileCreationService.createFileContent(LicenceType, "0001", "HEC_LICENCE_TYPE")
        //In future if content grows we can reduce the test to check only for few lines or header and trailer.
        //As of now content is less, so no harm in testing i guess
        //same applies for other test
        val expected                                = s"""|00|HEC_SSA_0001_20211010_HEC_LICENCE_TYPE.dat|HEC|SSA|20211010|113605|000001|001
             |01|00|Driver of taxis and private hires
             |01|01|Operator of private hire vehicles
             |01|02|Scrap metal mobile collector
             |01|03|Scrap metal dealer site
             |99|HEC_SSA_0001_20211010_HEC_LICENCE_TYPE.dat|6|Y""".stripMargin

        result shouldBe (Right((expected, "HEC_SSA_0001_20211010_HEC_LICENCE_TYPE.dat")))

      }

      "content Type is licence Time Trading" in {

        inSequence {
          mockDateProviderToday(LocalDate.of(2021, 10, 10))
          mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
        }
        val result: Either[Error, (String, String)] =
          fileCreationService.createFileContent(LicenceTimeTrading, "0001", "HEC_LICENCE_TIME_TRADING")
        val expected                                = s"""|00|HEC_SSA_0001_20211010_HEC_LICENCE_TIME_TRADING.dat|HEC|SSA|20211010|113605|000001|001
                           |01|00|0 to 2 years
                           |01|01|2 to 4 years
                           |01|02|4 to 8 years
                           |01|03|More than 8 years
                           |99|HEC_SSA_0001_20211010_HEC_LICENCE_TIME_TRADING.dat|6|Y""".stripMargin

        result shouldBe Right((expected, "HEC_SSA_0001_20211010_HEC_LICENCE_TIME_TRADING.dat"))

      }

      "content Type is licence Validity period" in {

        inSequence {
          mockDateProviderToday(LocalDate.of(2021, 10, 10))
          mockTimeProviderNow(LocalTime.of(11, 36, 5), zoneId)
        }
        val result: Either[Error, (String, String)] =
          fileCreationService.createFileContent(LicenceValidityPeriod, "0001", "HEC_LICENCE_VALIDITY_PERIOD")
        val expected                                = s"""|00|HEC_SSA_0001_20211010_HEC_LICENCE_VALIDITY_PERIOD.dat|HEC|SSA|20211010|113605|000001|001
                           |01|00|Up to 1 year
                           |01|01|Up to 2 years
                           |01|02|Up to 3 years
                           |01|03|Up to 4 years
                           |01|04|Up to 5 years
                           |99|HEC_SSA_0001_20211010_HEC_LICENCE_VALIDITY_PERIOD.dat|7|Y""".stripMargin

        result shouldBe Right((expected, "HEC_SSA_0001_20211010_HEC_LICENCE_VALIDITY_PERIOD.dat"))

      }

    }

    "return an error" when {

      "input type doesn't match the expected" in {
        val result: Either[Error, (String, String)] =
          fileCreationService.createFileContent("LicenceType", "0001", "LICENCE_TYPE")

        result.isLeft shouldBe true

      }
    }

  }

}
