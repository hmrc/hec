/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.hec.testonly.controllers

import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.hec.controllers.ControllerSpec
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.services.scheduleService.HecTaxCheckExtractionService

import scala.concurrent.Future

class FileTransferControllerSpec extends ControllerSpec {

  val mockHecTaxCheckExtractionService = mock[HecTaxCheckExtractionService]

  override val overrideBindings =
    List[GuiceableModule](
      bind[HecTaxCheckExtractionService].toInstance(mockHecTaxCheckExtractionService)
    )

  val controller = instanceOf[FileTransferController]

  def mockLockAndProcessHecData(result: Future[Option[Either[models.Error, Unit]]]) =
    (mockHecTaxCheckExtractionService.lockAndProcessHecData _)
      .expects()
      .returning(result)

  "FileTransferController" when {

    "handling requests to do a file transfer" must {

      def performAction(): Future[Result] =
        controller.fileTransfer(FakeRequest())

      "return a 409 (conflict) response" when {

        "a lock to trigger the job cannot be retrieved" in {
          mockLockAndProcessHecData(Future.successful(None))

          status(performAction()) shouldBe CONFLICT
        }

      }

      "throw an exception" when {

        "the future fails" in {
          mockLockAndProcessHecData(Future.failed(new Exception()))

          an[Exception] shouldBe thrownBy(await(performAction()))
        }

      }

      "return a 500 (internal server error) response" when {

        "the job fails" in {
          mockLockAndProcessHecData(Future.successful(Some(Left(Error("")))))

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a 200 (ok) response" when {

        "the job succeeds" in {
          mockLockAndProcessHecData(Future.successful(Some(Right(()))))

          status(performAction()) shouldBe OK
        }

      }

    }

  }

}
