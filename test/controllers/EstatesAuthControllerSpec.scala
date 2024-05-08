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

package controllers

import base.SpecBase
import config.AppConfig
import connectors.EnrolmentStoreConnector
import models.EnrolmentStoreResponse._
import models._
import org.mockito.ArgumentMatchers.{any, eq => mEq}
import org.mockito.Mockito.{mock, when}
import play.api.Application
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{EmptyRetrieval, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EstatesAuthControllerSpec extends SpecBase {

  private val utr = "0987654321"

  private val agentEnrolment = Enrolment("HMRC-AS-AGENT", List(EnrolmentIdentifier("AgentReferenceNumber", "SomeARN")), "Activated", None)
  private val estatesEnrolment = Enrolment("HMRC-TERS-ORG", List(EnrolmentIdentifier("SAUTR", utr)), "Activated", None)

  private val enrolments = Enrolments(Set(
    agentEnrolment,
    estatesEnrolment
  ))

  val mockEnrolmentStoreConnector: EnrolmentStoreConnector = mock(classOf[EnrolmentStoreConnector])

  override lazy val app: Application = applicationBuilder()
    .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
    .build()

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  private type RetrievalType = Option[String] ~ Option[AffinityGroup] ~ Enrolments

  private def authRetrievals(affinityGroup: AffinityGroup, enrolment: Enrolments) =
    Future.successful(new~(new~(Some("id"), Some(affinityGroup)), enrolment))

  "invoking the EstatesAuthController" when {

    "authenticating an agent" when {

      "estate is claimed and agent is authorised" must {

        "return OK" in {

          when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
            .thenReturn(authRetrievals(AffinityGroup.Agent, enrolments))

          val predicatedMatcher = mEq(
            Enrolment("HMRC-TERS-ORG")
              .withIdentifier("SAUTR", utr)
              .withDelegatedAuthRule("trust-auth")
          )

          when(mockAuthConnector.authorise(predicatedMatcher, mEq(EmptyRetrieval))(any(), any()))
            .thenReturn(Future.successful(()))

          when(mockEnrolmentStoreConnector.checkIfAlreadyClaimed(mEq(utr))(any(), any()))
            .thenReturn(Future.successful(AlreadyClaimed))

          val app = applicationBuilder()
            .configure("features.primaryEnrolmentCheck.enabled" -> false)
            .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
            .build()

          val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

          val result = route(app, request).value
          status(result) mustBe OK

          val response = contentAsJson(result).as[EstateAuthResponse]
          response mustBe EstateAuthAllowed()
        }
      }

      "estate has not been claimed by a representative" must {

        "redirect to estate not claimed page when primary enrolment check enabled" in {

          when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
            .thenReturn(authRetrievals(AffinityGroup.Agent, enrolments))

          when(mockEnrolmentStoreConnector.checkIfAlreadyClaimed(mEq(utr))(any[HeaderCarrier], any[ExecutionContext]))
            .thenReturn(Future.successful(NotClaimed))

          val app = applicationBuilder()
            .configure("features.primaryEnrolmentCheck.enabled" -> true)
            .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
            .build()

          val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

          val result = route(app, request).value
          status(result) mustBe OK

          val response = contentAsJson(result).as[EstateAuthResponse]
          response mustBe EstateAuthDenied(appConfig.estateNotClaimedUrl)
        }
      }

      "agent has not been authorised for any estates" must {

        "redirect to agent not authorised when primary enrolment check enabled" in {

          val enrolments = Enrolments(Set(agentEnrolment))

          when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
            .thenReturn(authRetrievals(AffinityGroup.Agent, enrolments))

          val predicatedMatcher = mEq(
            Enrolment("HMRC-TERS-ORG")
              .withIdentifier("SAUTR", utr)
              .withDelegatedAuthRule("trust-auth")
          )

          when(mockAuthConnector.authorise(predicatedMatcher, mEq(EmptyRetrieval))(any(), any()))
            .thenReturn(Future.failed(InsufficientEnrolments()))

          when(mockEnrolmentStoreConnector.checkIfAlreadyClaimed(mEq(utr))(any(), any()))
            .thenReturn(Future.successful(AlreadyClaimed))

          val app = applicationBuilder()
            .configure("features.primaryEnrolmentCheck.enabled" -> true)
            .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
            .build()

          val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

          val result = route(app, request).value
          status(result) mustBe OK

          val response = contentAsJson(result).as[EstateAuthResponse]
          response mustBe EstateAuthDenied(appConfig.agentNotAuthorisedUrl)
        }

      }

      "an agent that has an estates enrolment without matching submitted utr" must {

        "redirect to agent not authorised when primary enrolment check enabled" in {

          val enrolments = Enrolments(Set(
            agentEnrolment,
            Enrolment("HMRC-TERS-ORG", List(EnrolmentIdentifier("SAUTR", "1234567890")), "Activated", None)
          ))

          when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
            .thenReturn(authRetrievals(AffinityGroup.Agent, enrolments))

          val predicatedMatcher = mEq(
            Enrolment("HMRC-TERS-ORG")
              .withIdentifier("SAUTR", utr)
              .withDelegatedAuthRule("trust-auth")
          )

          when(mockAuthConnector.authorise(predicatedMatcher, mEq(EmptyRetrieval))(any(), any()))
            .thenReturn(Future.failed(InsufficientEnrolments()))


          when(mockEnrolmentStoreConnector.checkIfAlreadyClaimed(mEq(utr))(any(), any()))
            .thenReturn(Future.successful(AlreadyClaimed))

          val app = applicationBuilder()
            .configure("features.primaryEnrolmentCheck.enabled" -> true)
            .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
            .build()

          val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

          val result = route(app, request).value
          status(result) mustBe OK

          val response = contentAsJson(result).as[EstateAuthResponse]
          response mustBe EstateAuthDenied(appConfig.agentNotAuthorisedUrl)
        }

        "redirect to agent not authorised when primary enrolment check not enabled" in {

          val enrolments = Enrolments(Set(
            agentEnrolment,
            Enrolment("HMRC-TERS-ORG", List(EnrolmentIdentifier("SAUTR", "1234567890")), "Activated", None)
          ))

          when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
            .thenReturn(authRetrievals(AffinityGroup.Agent, enrolments))

          val predicatedMatcher = mEq(
            Enrolment("HMRC-TERS-ORG")
              .withIdentifier("SAUTR", utr)
              .withDelegatedAuthRule("trust-auth")
          )

          when(mockAuthConnector.authorise(predicatedMatcher, mEq(EmptyRetrieval))(any(), any()))
            .thenReturn(Future.failed(InsufficientEnrolments()))

          val app = applicationBuilder()
            .configure("features.primaryEnrolmentCheck.enabled" -> false)
            .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
            .build()

          val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

          val result = route(app, request).value
          status(result) mustBe OK

          val response = contentAsJson(result).as[EstateAuthResponse]
          response mustBe EstateAuthDenied(appConfig.agentNotAuthorisedUrl)
        }
      }

    }

    "authenticating an organisation user" when {

      "organisation user has an enrolment for the estate" when {

        "relationship does not exist in Estate IV" must {

          "redirect to estate IV for a non claiming check" in {

            val enrolments = Enrolments(Set(estatesEnrolment))

            when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
              .thenReturn(authRetrievals(AffinityGroup.Organisation, enrolments))

            when(mockAuthConnector.authorise(any[Relationship], mEq(EmptyRetrieval))(any(), any()))
              .thenReturn(Future.failed(FailedRelationship()))

            val app = applicationBuilder()
              .configure("features.primaryEnrolmentCheck.enabled" -> false)
              .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
              .build()

            val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

            val result = route(app, request).value
            status(result) mustBe OK

            val response = contentAsJson(result).as[EstateAuthDenied]
            response.redirectUrl must include("/maintain-this-estate")
          }

        }

        "relationship does exist in Estate IV" must {

          "return OK" in {

            val enrolments = Enrolments(Set(estatesEnrolment))

            when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
              .thenReturn(authRetrievals(AffinityGroup.Organisation, enrolments))

            when(mockAuthConnector.authorise(any[Relationship], mEq(EmptyRetrieval))(any(), any()))
              .thenReturn(Future.successful(()))

            val app = applicationBuilder()
              .configure("features.primaryEnrolmentCheck.enabled" -> false)
              .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
              .build()

            val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

            val result = route(app, request).value
            status(result) mustBe OK

            val response = contentAsJson(result).as[EstateAuthResponse]
            response mustBe EstateAuthAllowed()
          }
        }
      }

      "organisation user has no enrolment for the estate" when {

        "unable to determine if the UTR belongs to a different org account" must {

          "redirect to tech difficulties" in {
            val enrolments = Enrolments(Set())

            when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
              .thenReturn(authRetrievals(AffinityGroup.Organisation, enrolments))

            when(mockEnrolmentStoreConnector.checkIfAlreadyClaimed(mEq(utr))(any(), any()))
              .thenReturn(Future.successful(ServerError))

            val app = applicationBuilder()
              .configure("features.primaryEnrolmentCheck.enabled" -> false)
              .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
              .build()

            val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

            val result = route(app, request).value
            status(result) mustBe INTERNAL_SERVER_ERROR
          }
        }

        "utr is already claimed by a different org account" must {

          "redirect to already claimed" in {

            val enrolments = Enrolments(Set())

            when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
              .thenReturn(authRetrievals(AffinityGroup.Organisation, enrolments))

            when(mockEnrolmentStoreConnector.checkIfAlreadyClaimed(mEq(utr))(any(), any()))
              .thenReturn(Future.successful(AlreadyClaimed))

            val app = applicationBuilder()
              .configure("features.primaryEnrolmentCheck.enabled" -> false)
              .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
              .build()

            val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

            val result = route(app, request).value
            status(result) mustBe OK

            val response = contentAsJson(result).as[EstateAuthResponse]
            response mustBe EstateAuthDenied(appConfig.alreadyClaimedUrl)
          }
        }

        "utr is not already claimed by an org account" must {

          "redirect to claim an estate" in {

            val enrolments = Enrolments(Set())

            when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
              .thenReturn(authRetrievals(AffinityGroup.Organisation, enrolments))

            when(mockEnrolmentStoreConnector.checkIfAlreadyClaimed(mEq(utr))(any(), any()))
              .thenReturn(Future.successful(NotClaimed))

            val app = applicationBuilder()
              .configure("features.primaryEnrolmentCheck.enabled" -> false)
              .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
              .build()

            val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

            val result = route(app, request).value
            status(result) mustBe OK

            val response = contentAsJson(result).as[EstateAuthDenied]
            response.redirectUrl must include("/claim-an-estate")
          }
        }
      }
    }
  }

  "passing a non authenticated request" must {

    "redirect to the login page" in {

      val app = applicationBuilder()
        .configure("features.primaryEnrolmentCheck.enabled" -> false)
        .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
        .build()

      when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
        .thenReturn(Future failed BearerTokenExpired())

      val request = FakeRequest(GET, controllers.routes.EstatesAuthController.authorisedForUtr(utr).url)

      val futuristicResult = route(app, request).value
      recoverToSucceededIf[BearerTokenExpired](futuristicResult)
    }
  }

  "invoking authenticate" when {

    "an Agent user hasn't enrolled an Agent Services Account" must {

      "redirect the user to the create agent services page" in {

        val noEnrollment = Enrolments(Set())

        val app = applicationBuilder()
          .configure("features.primaryEnrolmentCheck.enabled" -> false)
          .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
          .build()

        when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
          .thenReturn(authRetrievals(AffinityGroup.Agent, noEnrollment))

        val request = FakeRequest(GET, controllers.routes.EstatesAuthController.agentAuthorised().url)
        val result = route(app, request).value

        status(result) mustBe OK

        val response = contentAsJson(result).as[EstateAuthResponse]
        response mustBe EstateAuthDenied(appConfig.createAgentServicesAccountUrl)
      }
    }
    "Agent user has correct enrolled in Agent Services Account" must {
      "allow authentication" in {
        val agentEnrolments = Enrolments(Set(agentEnrolment))

        val app = applicationBuilder()
          .configure("features.primaryEnrolmentCheck.enabled" -> false)
          .overrides(bind[EnrolmentStoreConnector].toInstance(mockEnrolmentStoreConnector))
          .build()

        when(mockAuthConnector.authorise(any(), any[Retrieval[RetrievalType]]())(any(), any()))
          .thenReturn(authRetrievals(AffinityGroup.Agent, agentEnrolments))

        val request = FakeRequest(GET, controllers.routes.EstatesAuthController.agentAuthorised().url)
        val result = route(app, request).value

        status(result) mustBe OK

        val response = contentAsJson(result).as[EstateAuthResponse]
        response mustBe EstateAuthAgentAllowed("SomeARN")
      }
    }
  }
}
