/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import cats.data.EitherT
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import uk.gov.hmrc.hec.models.Error
import uk.gov.hmrc.hec.models.hecTaxCheck.HECTaxCheck
import uk.gov.hmrc.hec.repos.HECTaxCheckStore
import uk.gov.hmrc.hec.services.MongoLockService

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.Future

class ResetTaxChecksServiceSpec
    extends TestKit(
      ActorSystem(
        "reset-tax-check-service",
        ConfigFactory
          .defaultApplication()
          .resolve()
          .withValue("akka.test.single-expect-default", ConfigValueFactory.fromAnyRef("3 seconds"))
      )
    )
    with AnyWordSpecLike
    with Matchers
    with MockFactory
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  def mockWithLock(lockId: String, lockObtained: Boolean) =
    (mockMongoLockService
      .withLock(_: String, _: Future[Either[Error, List[HECTaxCheck]]])(_: HECTaxCheckExtractionContext))
      .expects(lockId, *, *)
      .onCall { test =>
        if (lockObtained)
          test.productElement(1).asInstanceOf[() => Future[Either[Error, List[HECTaxCheck]]]]().map(Some(_))
        else
          Future.successful(None)
      }

  def mockResetTaxCheckIsExtractedFlag(resetTaxChecksCreatedOnOrAfter: ZonedDateTime)(result: Either[Error, Unit]) =
    (mockTaxCheckStore.resetTaxCheckIsExtractedFlag _)
      .expects(resetTaxChecksCreatedOnOrAfter)
      .returning(EitherT.fromEither(result))

  val mockMongoLockService = mock[MongoLockService]

  val mockTaxCheckStore = mock[HECTaxCheckStore]

  implicit val hecTaxCheckExtractionContext: HECTaxCheckExtractionContext = new HECTaxCheckExtractionContext(system)

  class Context(resetEnabled: Boolean) {

    val lockId: String = "reset-tax-check-sent-flags"

    val resetTaxChecksCreatedOnOrAfter: ZonedDateTime =
      ZonedDateTime.now(ZoneId.of("Z"))

    private val resetTaxChecksCreatedOnOrAfterString: String =
      resetTaxChecksCreatedOnOrAfter.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

    val configuration = Configuration(
      ConfigFactory.parseString(
        s"""
          |hec-file-extraction-details {
          |  reset-sent-flags {
          |    enabled = $resetEnabled
          |    reset-tax-checks-created-on-or-after = "$resetTaxChecksCreatedOnOrAfterString"
          |  }
          |}
          |""".stripMargin
      )
    )

    def startService(): Unit = {
      val _ = new ResetTaxChecksSentFlagService(configuration, mockMongoLockService, mockTaxCheckStore)
    }

  }

  "ResetTaxChecksServiceSpec" must {

    "not run the job" when {

      "the job is disabled" in new Context(resetEnabled = false) {
        startService()
      }

      "the job is enabled but a lock could not be obtained" in new Context(resetEnabled = true) {
        mockWithLock(lockId, lockObtained = false)

        startService()
      }

    }

    "attempt to reset 'sent' flags on all tax check data" when {

      "the job in enabled and a lock can be obtained and" when {

        "the job succeeds" in new Context(resetEnabled = true) {
          inSequence {
            mockWithLock(lockId, lockObtained = true)
            mockResetTaxCheckIsExtractedFlag(resetTaxChecksCreatedOnOrAfter)(Right(()))
          }

          startService()
        }

        "the job fails" in new Context(resetEnabled = true) {
          inSequence {
            mockWithLock(lockId, lockObtained = true)
            mockResetTaxCheckIsExtractedFlag(resetTaxChecksCreatedOnOrAfter)(Left(Error("")))
          }

          startService()
        }

      }

    }
  }

}
