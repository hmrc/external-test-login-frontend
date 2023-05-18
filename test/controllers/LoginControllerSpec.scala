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

import akka.stream.Materializer
import config.FrontendAppConfig
import handlers.ErrorHandler
import helpers.AsyncHmrcSpec
import models.{LoginFailed, LoginRequest}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{ContinueUrlService, LoginService}
import views.html.LoginView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class LoginControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite {

  trait Setup {
    implicit val materializer = app.injector.instanceOf[Materializer]
    private val csrfAddToken  = app.injector.instanceOf[play.filters.csrf.CSRFAddToken]

    val messagesApi: MessagesApi               = app.injector.instanceOf[MessagesApi]
    val loginService: LoginService             = mock[LoginService]
    val continueUrlService: ContinueUrlService = mock[ContinueUrlService]
    implicit val appConfig: FrontendAppConfig  = app.injector.instanceOf[FrontendAppConfig]
    val errorHandler: ErrorHandler             = app.injector.instanceOf[ErrorHandler]
    val mcc                                    = app.injector.instanceOf[MessagesControllerComponents]
    val loginView                              = app.injector.instanceOf[LoginView]

    val underTest = new LoginController(loginService, errorHandler, continueUrlService, mcc, loginView)

    def execute[T <: play.api.mvc.AnyContent](action: Action[AnyContent], request: FakeRequest[T] = FakeRequest()): Future[Result] =
      csrfAddToken(action)(request)
  }

  "showLoginPage" should {

    "display the login page" in new Setup {

      when(continueUrlService.isValidContinueUrl(*)).thenReturn(true)

      val result = execute(underTest.showLoginPage())

      contentAsString(result) should include("Sign in")
    }

    "display the login page when user id and password provided" in new Setup {

      when(continueUrlService.isValidContinueUrl(*)).thenReturn(true)

      val result = execute(underTest.showLoginPageWithCreds("foo", "bar"))

      contentAsString(result) should include("Sign in")
    }
  }

  "login" should {

    val loginRequest = LoginRequest("aUser", "aPassword")
    val continueUrl  = "/continueUrl"
    val request = FakeRequest()
      .withFormUrlEncodedBody("userId" -> loginRequest.username, "password" -> loginRequest.password, "continue" -> continueUrl)

    "display invalid userId or password when the credentials are invalid" in new Setup {
      when(continueUrlService.isValidContinueUrl(*)).thenReturn(true)
      when(loginService.authenticate(refEq(loginRequest))(*, *)).thenReturn(failed(LoginFailed("")))

      val result = execute(underTest.login(), request = request)

      contentAsString(result) should include("Invalid user ID or password. Try again.")
    }

    "return a 400 when the continue URL is not valid" in new Setup {
      when(continueUrlService.isValidContinueUrl(*)).thenReturn(false)

      val result = execute(underTest.login(), request = request)

      status(result) shouldBe 400
    }

    "redirect to the continueUrl with the session when the credentials are valid and the continue URL is valid" in new Setup {
      when(continueUrlService.isValidContinueUrl(*)).thenReturn(true)
      val sessionIn = Session(Map("authBearerToken" -> "Bearer AUTH_TOKEN"))
      when(loginService.authenticate(refEq(loginRequest))(*, *)).thenReturn(successful(sessionIn))

      val result = underTest.login()(request)

      status(result) shouldBe 303
      header("Location", result) shouldEqual Some(continueUrl)
      session(result).get("authBearerToken") shouldBe Some("Bearer AUTH_TOKEN")
    }
  }
}
