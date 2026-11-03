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

import play.api.libs.json.{Format, Json, Reads, Writes, __}

sealed trait EstateAuthResponse

object EstateAuthResponse {

  given reads: Reads[EstateAuthResponse] = (__ \ "arn")
    .read[String]
    .map[EstateAuthResponse](EstateAuthAgentAllowed.apply)
    .orElse(
      (__ \ "redirectUrl")
        .read[String]
        .map[EstateAuthResponse](EstateAuthDenied.apply)
    )
    .orElse(
      (__ \ "authorised")
        .read[Boolean]
        .map[EstateAuthResponse](EstateAuthAllowed.apply)
    )

  given writes: Writes[EstateAuthResponse] = Writes {
    case r: EstateAuthAllowed          => Json.toJson(r)(using EstateAuthAllowed.format)
    case r: EstateAuthAgentAllowed     => Json.toJson(r)(using EstateAuthAgentAllowed.format)
    case r: EstateAuthDenied           => Json.toJson(r)(using EstateAuthDenied.format)
    case EstateAuthInternalServerError =>
      throw new RuntimeException("Can't write EstateAuthInternalServerError as Json")
  }

}

case class EstateAuthAllowed(authorised: Boolean = true) extends EstateAuthResponse

case object EstateAuthAllowed {
  given format: Format[EstateAuthAllowed] = Json.format[EstateAuthAllowed]
}

case class EstateAuthAgentAllowed(arn: String) extends EstateAuthResponse

case object EstateAuthAgentAllowed {
  given format: Format[EstateAuthAgentAllowed] = Json.format[EstateAuthAgentAllowed]
}

case class EstateAuthDenied(redirectUrl: String) extends EstateAuthResponse

case object EstateAuthDenied {
  given format: Format[EstateAuthDenied] = Json.format[EstateAuthDenied]
}

case object EstateAuthInternalServerError extends EstateAuthResponse
