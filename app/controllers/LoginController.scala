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
import models.{LoginFailed, LoginRequest}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{AnyContent, MessagesControllerComponents, Request}
import services.{ContinueUrlService, LoginService}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.LoginView

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

@Singleton
class LoginController @Inject() (
  loginService: LoginService,
  errorHandler: ErrorHandler,
  continueUrlService: ContinueUrlService,
  mcc: MessagesControllerComponents,
  loginView: LoginView
)(implicit val mat: Materializer, val appConfig: FrontendAppConfig, val ec: ExecutionContext)
    extends FrontendController(mcc)
    with WithUnsafeDefaultFormBinding {

  case class LoginForm(userId: String, password: String, continue: String)

  private val loginForm = Form(
    mapping(
      "userId"   -> nonEmptyText,
      "password" -> nonEmptyText,
      "continue" -> nonEmptyText
    )(LoginForm.apply)(LoginForm.unapply)
  )

  def showLoginPage() = Action.async {
    implicit request =>
      successful(Ok(loginView(appConfig.continueUrl)))
  }

  def showLoginPageWithCreds(userId: String, password: String) = Action.async {
    implicit request =>
      successful(Ok(loginView(appConfig.continueUrl, None, Some(userId), Some(password))))
  }

  def login() = Action.async {
    implicit request =>
      def handleLogin(loginForm: LoginForm) =
        loginService.authenticate(LoginRequest(loginForm.userId, loginForm.password)) map {
          session =>
            Redirect(loginForm.continue).withSession(session)
        } recover {
          case e: LoginFailed =>
            Unauthorized(loginView(loginForm.continue, Some("Invalid user ID or password. Try again.")))
        }

      loginForm.bindFromRequest.fold(
        formWithErrors => badRequest(),
        loginForm => if (continueUrlService.isValidContinueUrl(loginForm.continue)) handleLogin(loginForm) else badRequest()
      )
  }

  private def badRequest()(implicit request: Request[AnyContent]) = successful(BadRequest(errorHandler.standardErrorTemplate("", "", "Invalid Parameters")))
}
