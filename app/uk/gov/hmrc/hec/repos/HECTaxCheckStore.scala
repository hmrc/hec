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

package uk.gov.hmrc.hec.repos

import cats.data.{EitherT, OptionT}
import cats.instances.either._
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsError, JsSuccess, Json, JsonValidationError}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.hec.models.ids.GGCredId
import uk.gov.hmrc.hec.models.{Error, HECTaxCheck, HECTaxCheckCode}
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HECTaxCheckStoreImpl])
trait HECTaxCheckStore {

  def get(taxCheckCode: HECTaxCheckCode)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Option[HECTaxCheck]]

  def getTaxCheckCodes(GGCredId: GGCredId)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]]

  def store(
    taxCheck: HECTaxCheck
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit]

  def delete(taxCheckCode: HECTaxCheckCode)(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit]

  def deleteAll()(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit]

  def getAllTaxCheckCodesByExtractedStatus(status: Boolean)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]]

}

@Singleton
class HECTaxCheckStoreImpl @Inject() (
  mongo: ReactiveMongoComponent,
  configuration: Configuration
)(implicit
  ec: ExecutionContext
) extends HECTaxCheckStore
    with Logging {

  val key: String                      = "hec-tax-check"
  private val ggCredIdField: String    = s"data.$key.taxCheckData.applicantDetails.ggCredId"
  private val isExtractedField: String = s"data.$key.isExtracted"

  private val hecIndexes = Seq(
    Index(Seq("ggCredId" -> IndexType.Ascending)),
    Index(
      Seq("isExtracted" -> IndexType.Ascending),
      partialFilter = Some(BSONDocument("isExtracted" -> false))
    )
  )

  val cacheRepository: CacheMongoRepository = {
    val expireAfter: FiniteDuration = configuration.underlying
      .get[FiniteDuration]("hec-tax-check.ttl")
      .value

    new CacheMongoRepository("hecTaxChecks", expireAfter.toSeconds)(
      mongo.mongoConnector.db,
      ec
    ) {

      //TODO temporary till the issue is resolved by the team owning cacheRepository code
      //issue- indexes were not getting created
      @SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.NonUnitStatements"))
      private def ensureIndex(index: Index)(implicit ec: ExecutionContext): Future[Boolean] =
        collection.indexesManager
          .create(index)
          .map { wr =>
            if (!wr.ok) {
              val msg      = wr.writeErrors.mkString(", ")
              val maybeMsg = if (msg.contains("E11000")) {
                // this is for backwards compatibility to mongodb 2.6.x

                throw GenericDatabaseException(msg, wr.code)
              } else Some(msg)
              logger.error(s"$message (${index.eventualName}) : '${maybeMsg.map(_.toString)}'")
            }
            wr.ok
          }
          .recover { case t =>
            logger.error(s"$message (${index.eventualName})", t)
            false
          }

      override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
        super.ensureIndexes.flatMap(_ => Future.sequence(indexes.map(ensureIndex)))

      override def indexes: Seq[Index] = hecIndexes
    }
  }

  def get(taxCheckCode: HECTaxCheckCode)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Option[HECTaxCheck]] =
    EitherT(
      preservingMdc {
        cacheRepository
          .findById(Id(taxCheckCode.value))
          .map { maybeCache =>
            val response: OptionT[Either[Error, *], HECTaxCheck] = for {
              cache ← OptionT.fromOption[Either[Error, *]](maybeCache)
              data ← OptionT.fromOption[Either[Error, *]](cache.data)
              result ← OptionT.liftF[Either[Error, *], HECTaxCheck](
                         (data \ key)
                           .validate[HECTaxCheck]
                           .asEither
                           .leftMap(e ⇒
                             Error(
                               s"Could not parse session data from mongo: ${e.mkString("; ")}"
                             )
                           )
                       )
            } yield result

            response.value
          }
          .recover { case e ⇒ Left(Error(e)) }
      }
    )

  /**
    * Fetch existing tax check codes for the specified GGCredId
    * @param ggCredId The government gateway ID
    * @param hc header information
    * @return A list of tax check code details
    */
  def getTaxCheckCodes(ggCredId: GGCredId)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]] = find(ggCredIdField -> ggCredId.value)

  def getAllTaxCheckCodesByExtractedStatus(isExtracted: Boolean)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]] = find(isExtractedField -> isExtracted)

  def store(
    taxCheck: HECTaxCheck
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] =
    EitherT(
      preservingMdc {
        cacheRepository
          .createOrUpdate(Id(taxCheck.taxCheckCode.value), key, Json.toJson(taxCheck))
          .map[Either[Error, Unit]] { dbUpdate ⇒
            if (dbUpdate.writeResult.inError)
              Left(
                Error(
                  dbUpdate.writeResult.errmsg.getOrElse(
                    "unknown error during inserting tax check in mongo"
                  )
                )
              )
            else
              Right(())
          }
          .recover { case e ⇒ Left(Error(e)) }
      }
    )

  def delete(taxCheckCode: HECTaxCheckCode)(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] =
    EitherT(
      preservingMdc {
        cacheRepository
          .removeById(Id(taxCheckCode.value))
          .map[Either[Error, Unit]] { writeResult ⇒
            if (!writeResult.ok)
              Left(
                Error(
                  s"Mongo write error: ${writeResult.writeErrors}"
                )
              )
            else
              Right(())
          }
          .recover { case e ⇒ Left(Error(e)) }
      }
    )

  def deleteAll()(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] =
    EitherT(
      preservingMdc {
        cacheRepository.drop
          .map[Either[Error, Unit]] { success ⇒
            if (!success)
              Left(Error("Could not drop mongo collection"))
            else
              Right(())
          }
          .recover { case e ⇒ Left(Error(e)) }
      }
    )

  private def find(
    query: (String, JsValueWrapper)
  ): EitherT[Future, Error, List[HECTaxCheck]] =
    EitherT(
      preservingMdc {
        cacheRepository
          .find(query)
          .map { caches =>
            val jsons = caches
              .flatMap(_.data.toList)
              .map(json => (json \ key).validate[HECTaxCheck])

            val (valid, invalid) =
              jsons.foldLeft((List.empty[HECTaxCheck], Seq.empty[JsonValidationError])) { (acc, json) =>
                val (taxChecks, errors) = acc
                json match {
                  case JsSuccess(value, _)       => (value +: taxChecks, errors)
                  case JsError(validationErrors) => (taxChecks, errors ++ validationErrors.flatMap(_._2))
                }
              }

            val errorStr = invalid.map(_.message).mkString("; ")
            if (invalid.nonEmpty) {
              logger.warn(s"${invalid.size} results failed json parsing - $errorStr")
            }

            Either.cond(invalid.isEmpty, valid, Error(Left(errorStr)))
          }
      }
    )
}
