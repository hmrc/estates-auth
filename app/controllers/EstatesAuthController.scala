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

package controllers

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Organisation}
import uk.gov.hmrc.auth.core.{EnrolmentIdentifier, Enrolments}
import config.AppConfig
import connectors.EnrolmentStoreConnector
import controllers.actions.IdentifierAction
import models._
import models.EnrolmentStoreResponse._
import services.{AgentAuthorisedForDelegatedEnrolment, EstatesIV}
import utils.{FunctionName, Session}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EstatesAuthController @Inject()(identifierAction: IdentifierAction,
                                      enrolmentStoreConnector: EnrolmentStoreConnector,
                                      appConfig: AppConfig,
                                      estatesIV: EstatesIV,
                                      delegatedEnrolment: AgentAuthorisedForDelegatedEnrolment
                               )(implicit cc: ControllerComponents, ec: ExecutionContext
) extends BackendController(cc) with I18nSupport with Logging {

  private def loggingPrefix(implicit fn: FunctionName, hc: HeaderCarrier) = s"[$fn][Session ID: ${Session.id(hc)}]"

  def authorisedForUtr(utr: String): Action[AnyContent] = identifierAction.async {
    implicit request =>
      implicit val hc : HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

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
        implicit val hc : HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        implicit val fn: FunctionName = FunctionName("checkIfEstateIsClaimedAndEstateIV")
        logger.info(s"$loggingPrefix not a valid agent service account")
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
    implicit val fn: FunctionName = FunctionName("checkIfEstateIsClaimedAndEstateIV")

    logger.info(s"$loggingPrefix authenticating user for $utr")

    if (userEnrolled) {
      logger.info(s"$loggingPrefix user is enrolled for $utr")

      estatesIV.authenticate(
        utr = utr,
        onIVRelationshipExisting = {
          logger.info(s"$loggingPrefix user has an IV session for $utr")
          Future.successful(EstateAuthAllowed())
        },
        onIVRelationshipNotExisting = {
          logger.info(s"$loggingPrefix user does not have an IV session for $utr")
          Future.successful(EstateAuthDenied(appConfig.maintainThisEstate))
        }
      )
    } else {
      enrolmentStoreConnector.checkIfAlreadyClaimed(utr) flatMap {
        case AlreadyClaimed =>
          logger.info(s"$loggingPrefix user is not enrolled for $utr and the estate is already claimed")
          Future.successful(EstateAuthDenied(appConfig.alreadyClaimedUrl))

        case NotClaimed =>
          logger.info(s"$loggingPrefix user is not enrolled for $utr and the estate is not claimed")
          Future.successful(EstateAuthDenied(appConfig.claimAnEstateUrl(utr)))
        case _ =>
          logger.info(s"$loggingPrefix unable to determine if $utr is already claimed")
          Future.successful(EstateAuthInternalServerError)
      }
    }
  }

  private def checkIfAgentAuthorised(utr: String)
                                       (implicit hc: HeaderCarrier): Future[EstateAuthResponse] = {

    if (appConfig.primaryEnrolmentCheckEnabled) {
      implicit val fn: FunctionName = FunctionName("checkIfAgentAuthorised")
      logger.info(s"$loggingPrefix authenticating agent for $utr")

      enrolmentStoreConnector.checkIfAlreadyClaimed(utr) flatMap {
        case NotClaimed =>
          logger.info(s"$loggingPrefix agent not authenticated for $utr, estate is not claimed")
          Future.successful(EstateAuthDenied(appConfig.estateNotClaimedUrl))
        case AlreadyClaimed =>
          logger.info(s"$loggingPrefix $utr is claimed, checking if agent is authorised")
          delegatedEnrolment.authenticate(utr)
        case _ =>
          logger.info(s"$loggingPrefix unable to determine if $utr is already claimed")
          Future.successful(EstateAuthInternalServerError)
      }
    } else {
      logger.info(s"[checkIfAgentAuthorised][Session ID: ${Session.id(hc)}] $utr checking if agent is authorised")
      delegatedEnrolment.authenticate(utr)
    }
  }

  private def checkForEstateEnrolmentForUTR[A](utr: String)(implicit request: IdentifierRequest[A]): Boolean =
    request.user.enrolments.enrolments
      .find(_.key equals "HMRC-TERS-ORG")
      .flatMap(_.identifiers.find(_.key equals "SAUTR"))
      .exists(_.value equals utr)
}
