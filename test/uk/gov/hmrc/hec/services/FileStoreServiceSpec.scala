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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.hec.services.scheduleService.HECTaxCheckExtractionContext
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.config.ObjectStoreClientConfig
import uk.gov.hmrc.objectstore.client.play.test.stub.StubPlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{Path, RetentionPeriod}

class FileStoreServiceSpec extends AnyWordSpec with Matchers with MockFactory with ScalaFutures with OptionValues {

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

    implicit val sys: ActorSystem                                           = ActorSystem("MyTest")
    implicit val hecTaxCheckExtractionContext: HECTaxCheckExtractionContext = new HECTaxCheckExtractionContext(sys)
    implicit val mat: Materializer                                          = Materializer(sys)
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
      fileStoreService.storeFile(fileContent, fileName, dirName)
      val expectedPath               = Path.File(s"$dirName/$fileName")
      val actual                     =
        client.getObject[Source[ByteString, NotUsed]](expectedPath).futureValue.value

      actual.location shouldBe s"${objectStoreConfig.baseUrl}/object-store/object/${objectStoreConfig.owner}/${expectedPath.asUri}"
      assertContent(actual.content, Source.single(ByteString(fileContent)))
    }

  }

}