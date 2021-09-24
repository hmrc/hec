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

package uk.gov.hmrc.hec.controllers.actions

import com.google.inject.ImplementedBy
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendHeaderCarrierProvider

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AuthenticatedRequest[+A](
  val ggCredId: String,
  request: Request[A]
) extends WrappedRequest[A](request)

@ImplementedBy(classOf[AuthenticateActionBuilder])
trait AuthenticateActions extends ActionBuilder[AuthenticatedRequest, AnyContent]

class AuthenticateActionBuilder @Inject() (
  val authConnector: AuthConnector,
  val parser: BodyParsers.Default,
  val executionContext: ExecutionContext
) extends AuthenticateActions
    with AuthorisedFunctions
    with BackendHeaderCarrierProvider {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    val forbidden = Results.Forbidden("Forbidden")
    val carrier   = hc(request)
    authorised(AuthProviders(GovernmentGateway))
      .retrieve(v2.Retrievals.credentials) {
        case Some(credentials) =>
          block(new AuthenticatedRequest[A](credentials.providerId, request))
        case _                 => Future.successful(forbidden)
      }(carrier, executionContext)
      .recover { case _: NoActiveSession =>
        forbidden
      }(executionContext)
  }
}
