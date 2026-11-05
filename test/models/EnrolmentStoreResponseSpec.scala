/*
 * Copyright 2026 HM Revenue & Customs
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

package models

import base.SpecBase
import models.EnrolmentStoreResponse.*
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

class EnrolmentStoreResponseSpec extends SpecBase {

  private def readResponse(status: Int, body: String = ""): EnrolmentStoreResponse = {
    val reads = EnrolmentStoreResponse.httpReads("0987654321")(using hc)
    reads.read("GET", "/test", HttpResponse(status, body))
  }

  "EnrolmentStoreResponse" should {

    "serialise and deserialise the enrolment store payload" in {
      val store = EnrolmentStore(Seq("principal-1"), Seq("delegate-1"))

      Json.toJson(store)                                    mustBe Json.obj(
        "principalUserIds" -> Json.arr("principal-1"),
        "delegatedUserIds" -> Json.arr("delegate-1")
      )

      Json.fromJson[EnrolmentStore](Json.toJson(store)).get mustBe store
    }

    "return NotClaimed when OK contains no principal user ids" in {
      val payload = Json.obj(
        "principalUserIds" -> Json.arr(),
        "delegatedUserIds" -> Json.arr()
      )

      readResponse(Status.OK, Json.stringify(payload)) mustBe NotClaimed
    }

    "return AlreadyClaimed when delegated ids exist even without principal ids" in {
      val payload = Json.obj(
        "principalUserIds" -> Json.arr(),
        "delegatedUserIds" -> Json.arr("delegate-1")
      )

      readResponse(Status.OK, Json.stringify(payload)) mustBe AlreadyClaimed
    }

    "map additional statuses to the expected response" in {
      readResponse(Status.NO_CONTENT)          mustBe NotClaimed
      readResponse(Status.SERVICE_UNAVAILABLE) mustBe ServiceUnavailable
      readResponse(Status.FORBIDDEN)           mustBe Forbidden
      readResponse(Status.BAD_REQUEST)         mustBe BadRequest
      readResponse(Status.IM_A_TEAPOT)         mustBe ServerError
    }
  }

}
