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

package uk.gov.hmrc.hec.services

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import akka.util.ByteString
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.hec.services.scheduleService.HECTaxCheckExtractionContext
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.config.ObjectStoreClientConfig
import uk.gov.hmrc.objectstore.client.play.test.stub.StubPlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Path, RetentionPeriod}

class FileStoreServiceSpec
    extends TestKit(
      ActorSystem(
        "file-storage-impl",
        ConfigFactory
          .defaultApplication()
          .resolve()
          .withValue("akka.test.single-expect-default", ConfigValueFactory.fromAnyRef("3 seconds"))
      )
    )
    with AnyWordSpecLike
    with Matchers
    with MockFactory
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "FileStoreServiceSpec" must {

    val config: Configuration = Configuration(
      ConfigFactory.parseString(
        """
          | object-store.default-retention-period = 1-week
          |""".stripMargin
      )
    )

    val objectStoreConfig = ObjectStoreClientConfig(
      baseUrl = "base",
      owner = "hec",
      authorizationToken = "123456",
      defaultRetentionPeriod = RetentionPeriod
        .parse(config.get[String]("object-store.default-retention-period"))
        .getOrElse(RetentionPeriod.OneWeek)
    )

    implicit val hecTaxCheckExtractionContext: HECTaxCheckExtractionContext = new HECTaxCheckExtractionContext(system)
    implicit val mat: Materializer                                          = Materializer(system)
    val client: StubPlayObjectStoreClient                                   = new StubPlayObjectStoreClient(objectStoreConfig)

    val fileStoreService: FileStoreServiceImpl = new FileStoreServiceImpl(client, config)
    val fileContent: String                    = s"00|file1.dat|HEC|SSA|20210909|154556|000001|001"
    val fileName: String                       = "file1.dat"
    val dirName: String                        = "licence-type"

    def assertContent(left: Source[ByteString, NotUsed], right: Source[ByteString, NotUsed])(implicit
      mat: Materializer
    ): Unit =
      (for {
        l <- left.runReduce(_ ++ _)
        r <- right.runReduce(_ ++ _)
      } yield l shouldBe r).futureValue

    "storeFile must store the file in object store " in {
      import uk.gov.hmrc.objectstore.client.play.Implicits._
      implicit val hc: HeaderCarrier = HeaderCarrier()
      await(fileStoreService.storeFile(fileContent, fileName, dirName).value)
      val expectedPath               = Path.File(s"$dirName/$fileName")
      val actual                     =
        await(client.getObject[Source[ByteString, NotUsed]](expectedPath)).getOrElse(sys.error("content is not there"))

      actual.location.directory.value shouldBe s"${objectStoreConfig.baseUrl}/object-store/object/${objectStoreConfig.owner}/$dirName"
      assertContent(actual.content, Source.single(ByteString(fileContent)))
    }

    "deleteObject must delete the file from object store " in {
      import uk.gov.hmrc.objectstore.client.play.Implicits._
      implicit val hc: HeaderCarrier = HeaderCarrier()
      client.deleteObject(Path.File(s"$dirName/$fileName"), objectStoreConfig.owner).futureValue
      val getObject                  = client
        .getObject[Source[ByteString, NotUsed]](Path.File(s"$dirName/$fileName"), objectStoreConfig.owner)
        .futureValue
      getObject shouldBe None
    }

  }

}
