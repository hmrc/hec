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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import org.apache.commons.codec.binary.StringUtils
import play.api.Configuration
import uk.gov.hmrc.hec.models
import uk.gov.hmrc.hec.services.scheduleService.HECTaxCheckExtractionContext
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{ObjectSummaryWithMd5, Path, RetentionPeriod}

import javax.inject.Singleton
import scala.concurrent.Future

@ImplementedBy(classOf[FileStoreServiceImpl])
trait FileStoreService {

  //store the file in object store
  def storeFile(
    fileContent: String,
    fileName: String,
    dirName: String
  )(implicit
    hc: HeaderCarrier,
    hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
  ): EitherT[Future, models.Error, ObjectSummaryWithMd5]

  def deleteFile(fileName: String, dirName: String)(implicit
    hc: HeaderCarrier,
    hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
  ): EitherT[Future, models.Error, Unit]

}

@Singleton
class FileStoreServiceImpl @Inject() (client: PlayObjectStoreClient, config: Configuration)
    extends FileStoreService
    with Logging {

  val owner: String = "hec"

  override def storeFile(
    fileContent: String,
    fileName: String,
    dirName: String
  )(implicit
    hc: HeaderCarrier,
    hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
  ): EitherT[Future, models.Error, ObjectSummaryWithMd5] = {
    val bytes = StringUtils.getBytesUtf8(fileContent)
    import uk.gov.hmrc.objectstore.client.play.Implicits._
    EitherT(
      client
        .putObject(
          path = Path.Directory(dirName).file(fileName),
          content = StringUtils.newStringUtf8(bytes),
          retentionPeriod = getRetentionPeriod,
          owner = owner
        )
        .map { objSummary =>
          logger.info(s"Saved File :: $fileName in object store")
          Right(objSummary)
        }
        .recover { case e: Exception =>
          logger.error(s"Document save failed for file :: $fileName error: ${e.getMessage}")
          Left(models.Error(e))
        }
    )
  }

  private def getRetentionPeriod: RetentionPeriod = RetentionPeriod
    .parse(config.get[String]("object-store.default-retention-period"))
    .getOrElse(RetentionPeriod.OneWeek)

  override def deleteFile(fileName: String, dirName: String)(implicit
    hc: HeaderCarrier,
    hecTaxCheckExtractionContext: HECTaxCheckExtractionContext
  ): EitherT[Future, models.Error, Unit] =
    EitherT(
      client
        .deleteObject(
          path = Path.Directory(dirName).file(fileName),
          owner = owner
        )
        .map { _ =>
          logger.info(s"Deleted file  :: $fileName from object store")
          Right(())
        }
        .recover { case e: Exception =>
          logger.error(s"Deletion failed for file:: $fileName with error: ${e.getMessage}")
          Left(models.Error(e))
        }
    )

}
