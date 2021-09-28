package uk.gov.hmrc.hec.services

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.joda.time.LocalDateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.models.HECTaxCheck
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class HecTaxCheckExtractionServiceImplSpec
    extends TestKit(ActorSystem.create("hec-tax-check-dispatcher"))
    with AnyWordSpecLike
    with Matchers
    with MockFactory
    with Eventually
    with BeforeAndAfterAll {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
  implicit val hc: HeaderCarrier                          = HeaderCarrier()

  val mockHecTaxCheckScheduleService        = mock[HecTaxCheckScheduleService]
  implicit val hecTaxCheckExtractionContext = new HECTaxCheckExtractionContext(system)

  def config(time: String) = Configuration(
    ConfigFactory.parseString(
      s"""
        |hec-file-extraction-details {
        |   extraction-timezone   = "Europe/London"
        |   extraction-time       = $time
        |   interval = 1 day
        |}
        |""".stripMargin
    )
  )

  val timeNow            = LocalDateTime.now()
  val timeString: String = s"${timeNow.getHourOfDay}:${timeNow.getMinuteOfHour}"
  val configuration      = config(timeString)

  def mockScheduleJob()(response: Option[Either[models.Error, List[HECTaxCheck]]]) =
    (mockHecTaxCheckScheduleService.scheduleJob _)
      .expects()
      .returning(Future.successful(response))

  "HECTaxCheckExtractionService" must {

    "not schedule a job" when {

      "there is already a lock on the job" in {
        val onCompleteListener = TestProbe()
        inSequence {
          mockScheduleJob()(None)
        }

        val _ =
          new HECTaxCheckExtractionService(
            system,
            mockHecTaxCheckScheduleService,
            configuration
          )

      }
    }

  }

}
