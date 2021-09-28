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
import cats.implicits._
import com.google.inject.{ImplementedBy, Inject}
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.HECTaxCheck
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Singleton
import scala.concurrent.Future

@ImplementedBy(classOf[HecTaxCheckScheduleServiceImpl])
trait HecTaxCheckScheduleService {
  def scheduleJob(): Future[Option[Either[models.Error, List[HECTaxCheck]]]]
}

@Singleton
class HecTaxCheckScheduleServiceImpl @Inject() (lockKeeperService: LockKeeperService, taxCheckService: TaxCheckService)(
  implicit hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
) extends HecTaxCheckScheduleService
    with Logging {

  implicit val hc: HeaderCarrier                                                      = HeaderCarrier()
  override def scheduleJob(): Future[Option[Either[models.Error, List[HECTaxCheck]]]] =
    lockKeeperService.generateLockFor("hec-tax-check") tryLock {
      scheduleExtractionJob
    }

  private def scheduleExtractionJob()(implicit hc: HeaderCarrier): Future[Either[models.Error, List[HECTaxCheck]]] = {
    val result: EitherT[Future, models.Error, List[HECTaxCheck]] = for {
      hecTaxCheck    <- taxCheckService.getAllTaxCheckCodesByStatus(false)
      //TODO process to generate fields from extracted data will be called here
      newHecTaxCheck <- hecTaxCheck.traverse[EitherT[Future, models.Error, *], HECTaxCheck] { taxCheck =>
                          logger.info(s" Job submitted to extract hec Tax check code :: ${taxCheck.taxCheckCode.value}")
                          taxCheckService.updateTaxCheck(taxCheck)
                        }
    } yield newHecTaxCheck
    result.value
  }
}
