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

package controllers.actions

import com.google.inject.{ImplementedBy, Inject}
import play.api.Logging
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Individual, Organisation}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import models._
import utils.Session
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AuthenticatedIdentifierAction])
trait IdentifierAction extends ActionBuilder[IdentifierRequest, AnyContent] with ActionFunction[Request, IdentifierRequest]

class AuthenticatedIdentifierAction @Inject()(
                                               estatesAuthFunctions: EstatesAuthorisedFunctions,
                                               val parser: BodyParsers.Default
                                             )
                                             (implicit val executionContext: ExecutionContext) extends IdentifierAction with Logging {

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    val retrievals = Retrievals.internalId and
      Retrievals.affinityGroup and
      Retrievals.allEnrolments

    estatesAuthFunctions.authorised().retrieve(retrievals) {
      case Some(internalId) ~ Some(Agent) ~ enrolments =>
        logger.info(s"[Session ID: ${Session.id(hc)}] successfully identified as an Agent")
        block(IdentifierRequest(request, AgentUser(internalId, enrolments)))
      case Some(internalId) ~ Some(Organisation) ~ enrolments =>
        logger.info(s"[Session ID: ${Session.id(hc)}] successfully identified as Organisation")
        block(IdentifierRequest(request, OrganisationUser(internalId, enrolments)))
      case Some(internalId) ~ Some(Individual) ~ enrolments =>
        logger.info(s"[Session ID: ${Session.id(hc)}] Unauthorised due to affinityGroup being Individual")
        block(IdentifierRequest(request, IndividualUser(internalId, enrolments)))
      case _ =>
        logger.warn(s"[Session ID: ${Session.id(hc)}] Unable to retrieve internal id")
        throw new UnauthorizedException("Unable to retrieve internal Id")
    } recover {
      case e =>
        logger.warn(s"[Session ID: ${Session.id(hc)}] Unable to retrieve internal id due to exception ${e.getMessage}")
        Status(UNAUTHORIZED)
    }
  }
}
