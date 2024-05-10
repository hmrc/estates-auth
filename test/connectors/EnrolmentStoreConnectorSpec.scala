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

package connectors

import base.SpecBase
import models.EnrolmentStoreResponse._
import play.api.http.Status

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class EnrolmentStoreConnectorSpec extends SpecBase {

  lazy val enrolmentStoreConnector: EnrolmentStoreConnector = app.injector.instanceOf[EnrolmentStoreConnector]

  private lazy val serviceName = "HMRC-TERS-ORG"

  private val identifierKey = "SAUTR"
  private val identifier = "0987654321"

  private val principalId = Seq("ABCEDEFGI1234567")

  private lazy val enrolmentsUrl: String = s"/enrolment-store-proxy/enrolment-store/enrolments/$serviceName~$identifierKey~$identifier/users"

  "EnrolmentStoreConnector" when {

    "No Content when" must {
      "No Content 204" in {

        wiremock(
          enrolmentsUrl,
          expectedStatus = Status.NO_CONTENT,
          expectedResponse = None
        )

        val result = Await.result(enrolmentStoreConnector.checkIfAlreadyClaimed(identifier), Duration.Inf)
        result mustBe NotClaimed

      }
    }

    "Estate not claimed" must {
      "empty principalUserIds retrieved" in {

        wiremock(
          enrolmentsUrl,
          expectedStatus = Status.OK,
          expectedResponse = Some(
            s"""{
               |    "principalUserIds": [
               |    ],
               |    "delegatedUserIds": [
               |    ]
               |}""".stripMargin
          )
        )

        val result = Await.result(enrolmentStoreConnector.checkIfAlreadyClaimed(identifier), Duration.Inf)
        result mustBe NotClaimed

      }
    }

    "Internal Server Error" must {
      "unexpected status received" in {

        wiremock(
          enrolmentsUrl,
          expectedStatus = Status.IM_A_TEAPOT,
          expectedResponse = None
        )

        val result = Await.result(enrolmentStoreConnector.checkIfAlreadyClaimed(identifier), Duration.Inf)
        result mustBe ServerError

      }
    }

    "Cannot access estate when" must {
      "non-empty principalUserIds retrieved" in {

        wiremock(
          enrolmentsUrl,
          expectedStatus = Status.OK,
          expectedResponse = Some(
            s"""{
               |    "principalUserIds": [
               |       "${principalId.head}"
               |    ],
               |    "delegatedUserIds": [
               |    ]
               |}""".stripMargin
          ))

        val result = Await.result(enrolmentStoreConnector.checkIfAlreadyClaimed(identifier), Duration.Inf)
        result mustBe AlreadyClaimed

      }
    }

    "Service Unavailable when" must {
      "Service Unavailable 503" in {

        wiremock(
          enrolmentsUrl,
          expectedStatus = Status.SERVICE_UNAVAILABLE,
          expectedResponse = Some(
            """
              |{
              |   "errorCode": "SERVICE_UNAVAILABLE",
              |   "message": "Service temporarily unavailable"
              |}""".stripMargin
          ))

        val result = Await.result(enrolmentStoreConnector.checkIfAlreadyClaimed(identifier), Duration.Inf)
        result mustBe ServiceUnavailable

      }
    }

    "Forbidden when" must {
      "Forbidden 403" in {

        wiremock(
          enrolmentsUrl,
          expectedStatus = Status.FORBIDDEN,
          expectedResponse = Some(
            """
              |{
              |   "errorCode": "CREDENTIAL_CANNOT_PERFORM_ADMIN_ACTION",
              |   "message": "The User credentials are valid but the user does not have permission to perform the requested function"
              |}""".stripMargin
          ))

        val result = Await.result(enrolmentStoreConnector.checkIfAlreadyClaimed(identifier), Duration.Inf)
        result mustBe Forbidden

      }
    }

    "Invalid service when" must {
      "Bad Request 400" in {

        wiremock(
          enrolmentsUrl,
          expectedStatus = Status.BAD_REQUEST,
          expectedResponse = Some(
            """
              |{
              |   "errorCode": "INVALID_SERVICE",
              |   "message": "The provided service does not exist"
              |}""".stripMargin
          ))

        val result = Await.result(enrolmentStoreConnector.checkIfAlreadyClaimed(identifier), Duration.Inf)
        result mustBe BadRequest

      }
    }

  }

}
