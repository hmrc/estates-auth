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

package base

import controllers.actions.EstatesAuthorisedFunctions
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues, RecoverMethods}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.DataEvent
import utils.WireMockHelper

import scala.concurrent.{ExecutionContext, Future}

trait SpecBase extends PlaySpec
  with Matchers
  with GuiceOneAppPerSuite
  with ScalaFutures
  with EitherValues
  with RecoverMethods
  with OptionValues
  with WireMockHelper {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockAuditConnector: AuditConnector = mock(classOf[AuditConnector])
  val mockAuthConnector: AuthConnector = mock(classOf[AuthConnector])
  val estatesAuth = new EstatesAuthorisedFunctions(mockAuthConnector)

  when(mockAuditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
    .thenReturn(Future.successful(Success))

  def applicationBuilder(): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled" -> false,
        "auditing.enabled" -> true,
        "microservice.metrics.graphite.enabled" -> false,
        "features.primaryEnrolmentCheck.enabled" -> false,
        "microservice.services.enrolment-store-proxy.port" -> server.port()
      )
      .overrides(
        bind[AuditConnector].toInstance(mockAuditConnector),
        bind[EstatesAuthorisedFunctions].toInstance(estatesAuth)
      )

  override lazy val app: Application = applicationBuilder().build()
}
