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

package config

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class AppConfig @Inject()(config: Configuration) {

  lazy val unauthorisedUrl: String = config.get[String]("urls.unauthorised")
  lazy val alreadyClaimedUrl: String = config.get[String]("urls.alreadyClaimed")
  lazy val estateNotClaimedUrl: String = config.get[String]("urls.estateNotClaimed")
  lazy val agentNotAuthorisedUrl: String = config.get[String]("urls.agentNotAuthorised")
  lazy val createAgentServicesAccountUrl: String = config.get[String]("urls.createAgentServicesAccount")
  lazy val maintainThisEstate: String = config.get[String]("urls.maintainThisEstate")

  def claimAnEstateUrl(utr: String) =
    s"${config.get[String]("urls.startClaimAnEstate")}/$utr"

  lazy val relationshipName: String =
    config.get[String]("microservice.services.self.relationship-establishment.name")
  lazy val relationshipIdentifier: String =
    config.get[String]("microservice.services.self.relationship-establishment.identifier")

  lazy val enrolmentStoreProxyUrl: String = config.get[Service]("microservice.services.enrolment-store-proxy").baseUrl
  lazy val primaryEnrolmentCheckEnabled: Boolean = config.get[Boolean]("features.primaryEnrolmentCheck.enabled")
}
