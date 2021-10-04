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

import akka.util.Helpers.Requiring
import cats.data.{EitherT, OptionT}
import cats.instances.either._
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.mongo.MongoUtils

import java.util.concurrent.TimeUnit
import org.mongodb.scala.model.Filters
import play.api.Configuration
import play.api.libs.json.{JsError, JsSuccess, JsonValidationError}
import uk.gov.hmrc.hec.models.ids.GGCredId
import uk.gov.hmrc.hec.models.{Error, HECTaxCheck, HECTaxCheckCode}
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{CurrentTimestampSupport, MongoComponent}
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
  mongo: MongoComponent,
  configuration: Configuration
)(implicit
  ec: ExecutionContext
) extends MongoCacheRepository(
      mongoComponent = mongo,
      replaceIndexes = true,
      collectionName = "hecTaxChecks",
      ttl = configuration.get[FiniteDuration]("hec-tax-check.ttl").value,
      timestampSupport = new CurrentTimestampSupport(), // Provide a different one for testing
      cacheIdType = CacheIdType.SimpleCacheId // Here, CacheId to be represented with `String`
    )
    with HECTaxCheckStore
    with Logging {

  val key: String                      = "hec-tax-check"
  private val ggCredIdField: String    = s"data.$key.taxCheckData.applicantDetails.ggCredId"
  private val isExtractedField: String = s"data.$key.isExtracted"

  //had to create default index on lastUpdated explicitly as it was not getting created .
  //calling super.index was calling indexes field of PlayMongoRepository, which is final and hence was giving run time error.
  def defaultIndexes: Seq[IndexModel] = Seq(
    IndexModel(
      Indexes.ascending("modifiedDetails.lastUpdated"),
      IndexOptions()
        .background(false)
        .name("lastUpdatedIndex")
        .expireAfter((configuration.get[FiniteDuration]("hec-tax-check.ttl").value).toMillis, TimeUnit.MILLISECONDS)
    )
  )

  //indexes for hecTaxChecks collection
  def mongoNewIndexes: Seq[IndexModel] = Seq(
    IndexModel(
      Indexes.ascending("ggCredId")
    ),
    IndexModel(
      Indexes.ascending("isExtracted"),
      IndexOptions()
        .name("isExtractedIndex")
        .partialFilterExpression(BsonDocument("isExtracted" -> false))
    )
  )

  override def ensureIndexes: Future[Seq[String]] =
    MongoUtils.ensureIndexes(collection, defaultIndexes ++ mongoNewIndexes, false)

  def get(taxCheckCode: HECTaxCheckCode)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Option[HECTaxCheck]] =
    EitherT(
      preservingMdc {
        findById(taxCheckCode.value)
          .map { maybeCache =>
            val response: OptionT[Either[Error, *], HECTaxCheck] = for {
              cache ← OptionT.fromOption[Either[Error, *]](maybeCache)
              //even if there is no data , cache returns with cache {"id" : "code1", data : {}}
              //so added a logic if there is no data at all, then return None
              // but if there is, then proceed to validate json
              cacheLength = cache.data.keys.size
              data       <- OptionT.fromOption[Either[Error, *]](if (cacheLength == 0) None else Some(cache.data))
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
  ): EitherT[Future, Error, List[HECTaxCheck]] = find[String](ggCredIdField, ggCredId.value)

  def getAllTaxCheckCodesByExtractedStatus(isExtracted: Boolean)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[HECTaxCheck]] = find[Boolean](isExtractedField, isExtracted)

  def store(
    taxCheck: HECTaxCheck
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] =
    EitherT(
      preservingMdc {
        put[HECTaxCheck](taxCheck.taxCheckCode.value)(DataKey(key), taxCheck)
          .map(_ => Right(()))
          .recover { case e => Left(Error(e)) }
      }
    )

  def delete(taxCheckCode: HECTaxCheckCode)(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] =
    EitherT(
      preservingMdc {
        delete[HECTaxCheck](taxCheckCode.value)(DataKey(key))
          .map(Right(_))
          .recover { case e => Left(Error(e)) }
      }
    )

  def deleteAll()(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] = EitherT(
    preservingMdc {
      collection
        .drop()
        .toFuture()
        .map(_ => ())
        .map[Either[Error, Unit]](Right(_))
        .recover { case e => Left(Error(e)) }
    }
  )

  private def find[T](
    fieldName: String,
    value: T
  ): EitherT[Future, Error, List[HECTaxCheck]] =
    EitherT(
      preservingMdc {
        collection
          .find(Filters.equal[T](fieldName, value))
          .toFuture()
          .map { caches =>
            val jsons = caches
              .map(_.data)
              .toList
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
