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

package connectors

import config.FrontendAppConfig
import models.JsonFormatters._
import models._
import play.api.http.Status.{CREATED, OK}
import play.api.http.{HeaderNames, Status}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ApiPlatformTestUserConnector @Inject() (
  proxiedHttpClient: ProxiedHttpClient,
  appConfig: FrontendAppConfig,
  configuration: Configuration,
  environment: Environment,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext) {
  private val serviceKey = "api-platform-test-user"

  private val bearerToken = servicesConfig.getConfString(s"$serviceKey.bearer-token", "")

  private val httpClient = proxiedHttpClient.withAuthorization(bearerToken)

  val serviceUrl: String = {
    val context = servicesConfig.getConfString(s"$serviceKey.context", "")

    if (context.nonEmpty) {
      s"${servicesConfig.baseUrl(serviceKey)}/$context"
    } else {
      servicesConfig.baseUrl(serviceKey)
    }
  }

  // TODO - why does this have to be a TestIndividual and not a TestOrganisation?
  def createOrg(enrolments: Seq[String])(implicit hc: HeaderCarrier): Future[TestIndividual] = {
    val payload = CreateUserRequest(enrolments)

    post(s"$serviceUrl/organisations", payload) map {
      response =>
        response.status match {
          case CREATED =>
            response.json.as[TestIndividual]
          case _ => throw new RuntimeException(s"Unexpected response code=${response.status} message=${response.body}")
        }
    }
  }

  def getServices()(implicit hc: HeaderCarrier): Future[Seq[Service]] =
    httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](s"$serviceUrl/services") map {
      case Right(response) if response.status == OK => response.json.as[Seq[Service]]
      case Right(response) =>
        throw new RuntimeException(s"Unexpected response code=${response.status} message=${response.body}")
      case Left(UpstreamErrorResponse(body, status, _, _)) =>
        throw new RuntimeException(s"Unexpected response code=$status message=$body")
      case _ => throw new RuntimeException(s"Unexpected response")
    }

  def authenticate(loginRequest: LoginRequest)(implicit hc: HeaderCarrier): Future[AuthenticatedSession] =
    httpClient.POST[LoginRequest, Either[UpstreamErrorResponse, HttpResponse]](s"$serviceUrl/session", loginRequest) map {
      case Right(response) =>
        val authenticationResponse = response.json.as[AuthenticationResponse]

        (response.header(HeaderNames.AUTHORIZATION), response.header(HeaderNames.LOCATION)) match {
          case (Some(authBearerToken), Some(authorityUri)) =>
            AuthenticatedSession(
              authBearerToken,
              authorityUri,
              authenticationResponse.gatewayToken,
              authenticationResponse.affinityGroup
            )
          case _ => throw new RuntimeException("Authorization and Location headers must be present in response")
        }

      case Left(UpstreamErrorResponse(_, Status.UNAUTHORIZED, _, _)) => throw LoginFailed(loginRequest.username)
      case Left(err)                                                 => throw err
    }

  private def post(url: String, payload: CreateUserRequest)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClient.POST[CreateUserRequest, HttpResponse](url, payload, Seq("Content-Type" -> "application/json"))
}
