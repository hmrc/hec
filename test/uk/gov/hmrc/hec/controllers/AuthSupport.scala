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

package uk.gov.hmrc.hec.controllers

import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.*
import uk.gov.hmrc.hec.controllers.AuthSupport.*
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait AuthSupport { this: ControllerSpec =>

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuth[R](predicate: Predicate, retrieval: Retrieval[R])(
    result: Future[R]
  ): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[R])(
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(predicate, retrieval, *, *)
      .returning(result)

  def mockGGAuthWithGGRetrieval(ggCredId: String): Unit =
    mockAuth(AuthProviders(GovernmentGateway), Retrievals.credentials)(
      Future.successful(Some(Credentials(ggCredId, ggCredId)))
    )

  def mockGGAuthWithForbidden(): Unit =
    mockAuth(AuthProviders(GovernmentGateway), Retrievals.credentials)(
      Future.successful(None)
    )

  def mockGGOrStrideAuth(
    credentials: Option[Credentials],
    enrolments: Enrolments,
    email: Option[String]
  ): Unit =
    mockAuth(
      AuthProviders(GovernmentGateway, PrivilegedApplication),
      ggOrStrideAuthRetrievals
    )(
      Future.successful(
        new ~(credentials, enrolments) and email
      )
    )

  val ggOrStrideAuthRetrievals: Retrieval[Option[Credentials] ~ Enrolments ~ Option[String]] =
    Retrievals.credentials and Retrievals.allEnrolments and Retrievals.email

}

object AuthSupport {

  implicit class RetrievalOps[A, B](val r: ~[A, B]) {
    def and[C](c: C): ~[~[A, B], C] = new ~(r, c)
  }

}
