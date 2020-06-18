/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.estatesauth.controllers

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Organisation}
import uk.gov.hmrc.auth.core.{EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.estatesauth.config.AppConfig
import uk.gov.hmrc.estatesauth.connectors.EnrolmentStoreConnector
import uk.gov.hmrc.estatesauth.controllers.actions.IdentifierAction
import uk.gov.hmrc.estatesauth.models._
import uk.gov.hmrc.estatesauth.models.EnrolmentStoreResponse._
import uk.gov.hmrc.estatesauth.services.{AgentAuthorisedForDelegatedEnrolment, EstatesIV}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EstatesAuthController @Inject()(val controllerComponents: MessagesControllerComponents,
                                      identifierAction: IdentifierAction,
                                      enrolmentStoreConnector: EnrolmentStoreConnector,
                                      appConfig: AppConfig,
                                      estatesIV: EstatesIV,
                                      delegatedEnrolment: AgentAuthorisedForDelegatedEnrolment
                               )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  def authorisedForUtr(utr: String): Action[AnyContent] = identifierAction.async {
    implicit request =>
      implicit val hc : HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)

      mapResult(request.user.affinityGroup match {
        case Agent =>
          checkIfAgentAuthorised(utr)
        case Organisation =>
          checkIfEstateIsClaimedAndEstateIV(utr)
        case _ =>
          Future.successful(EstateAuthDenied(appConfig.unauthorisedUrl))
      })
  }

  def agentAuthorised(): Action[AnyContent] = identifierAction.async {
    implicit request =>
      implicit val hc : HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)

      mapResult(request.user.affinityGroup match {
        case Agent =>
          Future.successful(authoriseAgent(request))
        case Organisation =>
          Future.successful(EstateAuthAllowed)
        case _ =>
          Future.successful(EstateAuthDenied(appConfig.unauthorisedUrl))
      })
  }

  private def mapResult(result: Future[Object]) = result map {
    case EstateAuthInternalServerError => InternalServerError
    case r: EstateAuthResponse => Ok(Json.toJson(r))
  }

  private def authoriseAgent[A](request: IdentifierRequest[A]): EstateAuthResponse = {

    getAgentReferenceNumber(request.user.enrolments) match {
      case Some(arn) if arn.nonEmpty =>
        EstateAuthAgentAllowed(arn)
      case _ =>
        Logger.info(s"[authoriseAgent] not a valid agent service account")
        EstateAuthDenied(appConfig.createAgentServicesAccountUrl)
    }
  }

  private def getAgentReferenceNumber(enrolments: Enrolments) =
    enrolments.enrolments
      .find(_.key equals "HMRC-AS-AGENT")
      .flatMap(_.identifiers.find(_.key equals "AgentReferenceNumber"))
      .collect { case EnrolmentIdentifier(_, value) => value }

  private def checkIfEstateIsClaimedAndEstateIV[A](utr: String)
                                                (implicit request: IdentifierRequest[A],
                                                 hc: HeaderCarrier): Future[EstateAuthResponse] = {

    val userEnrolled = checkForEstateEnrolmentForUTR(utr)

    Logger.info(s"[checkIfEstateIsClaimedAndEstateIV] authenticating user for $utr")

    if (userEnrolled) {
      Logger.info(s"[checkIfEstateIsClaimedAndEstateIV] user is enrolled for $utr")

      estatesIV.authenticate(
        utr = utr,
        onIVRelationshipExisting = {
          Logger.info(s"[checkIfEstateIsClaimedAndEstateIV] user has an IV session for $utr")
          Future.successful(EstateAuthAllowed())
        },
        onIVRelationshipNotExisting = {
          Logger.info(s"[checkIfEstateIsClaimedAndEstateIV] user does not have an IV session for $utr")
          Future.successful(EstateAuthDenied(appConfig.maintainThisEstate))
        }
      )
    } else {
      enrolmentStoreConnector.checkIfAlreadyClaimed(utr) flatMap {
        case AlreadyClaimed =>
          Logger.info(s"[checkIfEstateIsClaimedAndEstateIV] user is not enrolled for $utr and the estate is already claimed")
          Future.successful(EstateAuthDenied(appConfig.alreadyClaimedUrl))

        case NotClaimed =>
          Logger.info(s"[checkIfEstateIsClaimedAndEstateIV] user is not enrolled for $utr and the estate is not claimed")
          Future.successful(EstateAuthDenied(appConfig.claimAnEstateUrl(utr)))
        case _ =>
          Logger.info(s"[checkIfEstateIsClaimedAndEstateIV] unable to determine if $utr is already claimed")
          Future.successful(EstateAuthInternalServerError)
      }
    }
  }

  private def checkIfAgentAuthorised[A](utr: String)
                                       (implicit request: Request[A],
                                        hc: HeaderCarrier): Future[EstateAuthResponse] = {

    Logger.info(s"[checkIfAgentAuthorised] authenticating agent for $utr")

    enrolmentStoreConnector.checkIfAlreadyClaimed(utr) flatMap {
      case NotClaimed =>
        Logger.info(s"[checkIfAgentAuthorised] agent not authenticated for $utr, estate is not claimed")
        Future.successful(EstateAuthDenied(appConfig.estateNotClaimedUrl))
      case AlreadyClaimed =>
        Logger.info(s"[checkIfAgentAuthorised] $utr is claimed, checking if agent is authorised")
        delegatedEnrolment.authenticate(utr)
      case _ =>
        Logger.info(s"[checkIfAgentAuthorised] unable to determine if $utr is already claimed")
        Future.successful(EstateAuthInternalServerError)
    }
  }

  private def checkForEstateEnrolmentForUTR[A](utr: String)(implicit request: IdentifierRequest[A]): Boolean =
    request.user.enrolments.enrolments
      .find(_.key equals "HMRC-TERS-ORG")
      .flatMap(_.identifiers.find(_.key equals "SAUTR"))
      .exists(_.value equals utr)
}
