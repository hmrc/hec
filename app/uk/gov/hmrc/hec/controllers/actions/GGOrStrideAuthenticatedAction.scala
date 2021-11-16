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

import com.github.ghik.silencer.silent
import com.google.inject.ImplementedBy
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.hec.util.Logging
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendHeaderCarrierProvider

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

final case class AuthenticatedGGOrStrideRequest[+A](
  ggCredId: Option[String],
  request: Request[A]
) extends WrappedRequest[A](request)

@ImplementedBy(classOf[GGOrStrideAuthenticateActionBuilder])
trait GGOrStrideAuthenticateAction extends ActionBuilder[AuthenticatedGGOrStrideRequest, AnyContent]

class GGOrStrideAuthenticateActionBuilder @Inject() (
  val authConnector: AuthConnector,
  val parser: BodyParsers.Default,
  val executionContext: ExecutionContext
) extends GGOrStrideAuthenticateAction
    with AuthorisedFunctions
    with BackendHeaderCarrierProvider
    with Logging {

  // authProviderId retrieval is deprecated but is necessary for the AuthProvider type PrivilegedApplication
  // since the recommended alternative retrieval does not work for it
  @silent("deprecated")
  override def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedGGOrStrideRequest[A] => Future[Result]
  ): Future[Result] = {
    val forbidden = Results.Forbidden("Forbidden")
    val carrier   = hc(request)

    authorised(AuthProviders(GovernmentGateway, PrivilegedApplication))
      .retrieve(v2.Retrievals.authProviderId) {
        case GGCredId(ggCredId) =>
          block(new AuthenticatedGGOrStrideRequest[A](Some(ggCredId), request))

        case PAClientId(_) =>
          block(new AuthenticatedGGOrStrideRequest[A](None, request))

        case other =>
          logger.info(s"Found unsupported auth provider id type: ${other.getClass.getSimpleName}")
          Future.successful(forbidden)
      }(carrier, executionContext)
      .recover { case _: NoActiveSession =>
        forbidden
      }(executionContext)
  }
}
