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

import uk.gov.hmrc.hec.services.scheduleService.HecTaxCheckExtractionServiceImpl.FileDetails

object FileMapOps {
  val hec: String           = "HEC"
  val sdesDirectory: String = "sdes"

  // Map with key as partial file name and fir as values
  val fileNameDirMap: Map[String, String] = Map(
    s"${hec}_LICENCE_TYPE"            -> s"$sdesDirectory/licence-type",
    s"${hec}_LICENCE_TIME_TRADING"    -> s"$sdesDirectory/licence-time-trading",
    s"${hec}_LICENCE_VALIDITY_PERIOD" -> s"$sdesDirectory/licence-validity-period",
    s"${hec}_CORRECTIVE_ACTION"       -> s"$sdesDirectory/corrective-action",
    s"${hec}_APPLICATION"             -> s"$sdesDirectory/tax-checks"
  )

  def getFileDetails[A](partialFileName: String): FileDetails[A] = {
    val dirName = getDirName(partialFileName)
    FileDetails[A](dirName, partialFileName)
  }

  def getDirName(partialFileName: String): String =
    fileNameDirMap.get(partialFileName).getOrElse(sys.error("fileName doesn't exists in map"))

}
