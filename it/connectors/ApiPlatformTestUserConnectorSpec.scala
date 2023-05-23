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

import com.github.tomakehurst.wiremock.client.WireMock._
import config.FrontendAppConfig
import helpers.{AsyncHmrcSpec, WiremockSugar}
import models.{AuthenticatedSession, Field, LoginFailed, LoginRequest, Service, UserTypes}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import models.JsonFormatters._
import play.api.http.HeaderNames.{AUTHORIZATION, LOCATION}
import play.api.libs.json.Json.toJson

import scala.concurrent.ExecutionContext.Implicits.global

class ApiPlatformTestUserConnectorSpec extends AsyncHmrcSpec with WiremockSugar with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(("metrics.jvm", false))
      .build()

  private val loginRequest = LoginRequest("user", "password")
  private val loginPayload = Json.toJson(LoginRequest("user", "password")).toString

  trait Setup {
    implicit val hc = HeaderCarrier()

    val underTest = new ApiPlatformTestUserConnector(
      app.injector.instanceOf[ProxiedHttpClient],
      app.injector.instanceOf[FrontendAppConfig],
      app.injector.instanceOf[Configuration],
      app.injector.instanceOf[Environment],
      app.injector.instanceOf[ServicesConfig]
    ) {
      override val serviceUrl: String = wireMockUrl
    }
  }

  "createOrg" should {
    "return a generated organisation" in new Setup {
      private val saUtr    = "1555369052"
      private val empRef   = "555/EIA000"
      private val userId   = "user"
      private val password = "password"

      val requestPayload =
        s"""{
           |  "serviceNames": [
           |    "national-insurance",
           |    "self-assessment",
           |    "mtd-income-tax"
           |  ]
           |}""".stripMargin

      stubFor(
        post(urlEqualTo("/organisations"))
          .withRequestBody(equalToJson(requestPayload))
          .willReturn(
            aResponse()
              .withStatus(CREATED)
              .withBody(s"""
                           |{
                           |  "userId":"$userId",
                           |  "password":"$password",
                           |  "individualDetails": {
                           |    "firstName": "Ida",
                           |    "lastName": "Newton",
                           |    "dateOfBirth": "1960-06-01",
                           |    "address": {
                           |      "line1": "45 Springfield Rise",
                           |      "line2": "Glasgow",
                           |      "postcode": "TS1 1PA"
                           |    }
                           |  },
                           |  "eoriNumber":"1555369052"
                           |}""".stripMargin)
          )
      )

      val result = await(underTest.createOrg(Seq("national-insurance", "self-assessment", "mtd-income-tax")))

      result.userId shouldBe userId
      result.password shouldBe password
      result.fields should contain(Field("eoriNumber", "Economic Operator Registration and Identification (EORI) number", saUtr))
    }

    "fail when api-platform-test-user returns a response that is not 201 CREATED" in new Setup {
      stubFor(
        post(urlEqualTo("/organisations"))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )

      intercept[RuntimeException](await(underTest.createOrg(Seq("national-insurance", "self-assessment", "mtd-income-tax"))))
    }
  }

  "getServices" when {
    "api-platform-test-user returns a 200 OK response" should {
      "return the services from api-platform-test-user" in new Setup {
        val services = Seq(Service("service-1", "Service One", Seq(UserTypes.INDIVIDUAL)))

        stubFor(
          get(urlEqualTo("/services"))
            .willReturn(
              aResponse()
                .withBody(
                  Json.toJson(services).toString()
                )
                .withStatus(OK)
            )
        )

        val result = await(underTest.getServices())

        result shouldBe services
      }
    }

    "api-platform-test-user returns a response other than 200 OK" should {
      "throw runtime exception" in new Setup {
        stubFor(
          get(urlEqualTo("/services"))
            .willReturn(
              aResponse()
                .withStatus(CREATED)
            )
        )

        intercept[RuntimeException](await(underTest.getServices()))
      }
    }
  }

  "authenticate" should {
    "return the auth session when the credentials are valid" in new Setup {
      val authBearerToken = "Bearer AUTH_TOKEN"
      val userOid         = "/auth/oid/12345"
      val gatewayToken    = "GG_TOKEN"
      val affinityGroup   = "Individual"

      stubFor(
        post(urlEqualTo("/session"))
          .withRequestBody(equalToJson(loginPayload))
          .willReturn(
            aResponse()
              .withStatus(CREATED)
              .withBody(Json.obj("gatewayToken" -> gatewayToken, "affinityGroup" -> affinityGroup).toString())
              .withHeader(AUTHORIZATION, authBearerToken)
              .withHeader(LOCATION, userOid)
          )
      )

      val result = await(underTest.authenticate(loginRequest))

      result shouldBe AuthenticatedSession(authBearerToken, userOid, gatewayToken, affinityGroup)
    }

    "fail with LoginFailed when the credentials are not valid" in new Setup {
      stubFor(
        post(urlEqualTo("/session"))
          .withRequestBody(equalToJson(toJson(loginRequest).toString()))
          .willReturn(
            aResponse()
              .withStatus(UNAUTHORIZED)
          )
      )

      intercept[LoginFailed] {
        await(underTest.authenticate(loginRequest))
      }
    }

    "fail when the authenticate call returns an error" in new Setup {
      stubFor(
        post(urlEqualTo("/session"))
          .withRequestBody(equalToJson(loginPayload))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      intercept[UpstreamErrorResponse] {
        await(underTest.authenticate(loginRequest))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }

  }
}
