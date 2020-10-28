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

package uk.gov.hmrc.estatesauth.services

import com.google.inject.Inject
import play.api.Logger
import uk.gov.hmrc.auth.core.{Enrolment, InsufficientEnrolments}
import uk.gov.hmrc.estatesauth.config.AppConfig
import uk.gov.hmrc.estatesauth.controllers.actions.EstatesAuthorisedFunctions
import uk.gov.hmrc.estatesauth.models.{EstateAuthAllowed, EstateAuthDenied, EstateAuthResponse}
import uk.gov.hmrc.estatesauth.utils.Session
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AgentAuthorisedForDelegatedEnrolment @Inject()(estatesAuth: EstatesAuthorisedFunctions, config: AppConfig) {

  private val logger: Logger = Logger(getClass)

  def authenticate[A](utr: String)
                     (implicit hc: HeaderCarrier,
                      ec: ExecutionContext): Future[EstateAuthResponse] = {

    val predicate = Enrolment("HMRC-TERS-ORG")
      .withIdentifier("SAUTR", utr)
      .withDelegatedAuthRule("trust-auth")

    estatesAuth.authorised(predicate) {
      logger.info(s"[Session ID: ${Session.id(hc)}][UTR: $utr] agent is authorised for delegated enrolment for $utr")
      Future.successful(EstateAuthAllowed())
    } recover {
      case _ : InsufficientEnrolments =>
        logger.info(s"[Session ID: ${Session.id(hc)}][UTR: $utr] agent is not authorised for delegated enrolment for $utr")
        EstateAuthDenied(config.agentNotAuthorisedUrl)
      case _ =>
        logger.info(s"[Session ID: ${Session.id(hc)}][UTR: $utr] agent is not authorised for $utr")
        EstateAuthDenied(config.unauthorisedUrl)
    }
  }
}
