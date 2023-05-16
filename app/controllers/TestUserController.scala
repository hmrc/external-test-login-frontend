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

import config.FrontendAppConfig
import connectors.ApiPlatformTestUserConnector
import logger.ApplicationLogger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.TestUserService
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{CreateTestUserViewGeneric, TestUserViewGeneric}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TestUserController @Inject() (
  override val messagesApi: MessagesApi,
  testUserService: TestUserService,
  apiPlatformTestUserConnector: ApiPlatformTestUserConnector,
  messagesControllerComponents: MessagesControllerComponents,
  createTestUserGeneric: CreateTestUserViewGeneric,
  testUserGeneric: TestUserViewGeneric
)(implicit val ec: ExecutionContext, config: FrontendAppConfig)
    extends FrontendController(messagesControllerComponents)
    with I18nSupport
    with ApplicationLogger
    with WithUnsafeDefaultFormBinding {

  def showCreateUserPageGeneric: Action[AnyContent] = Action.async {
    implicit request =>
      testUserService.services.flatMap(
        services =>
          Future.successful(
            Ok(
              createTestUserGeneric(services.filter(
                                      s => config.serviceKeys.contains(s.key)
                                    ),
                                    CreateUserForm.form
              )
            )
          )
      )
  }

  def createUserGeneric: Action[AnyContent] = Action.async {
    implicit request =>
      def validForm(form: CreateUserForm) = {
        val x = form.services

        x match {
          case Some(services) =>
            testUserService.createUser(services.split(",").toSeq) map (
              user => Ok(testUserGeneric(user))
            )
          case _ => Future.failed(new BadRequestException("Invalid request"))
        }
      }

      def invalidForm(invalidForm: Form[CreateUserForm]) =
        testUserService.services.flatMap(
          services =>
            Future.successful(
              BadRequest(
                createTestUserGeneric(services.filter(
                                        s => config.serviceKeys.contains(s.key)
                                      ),
                                      invalidForm
                )
              )
            )
        )

      CreateUserForm.form.bindFromRequest().fold(invalidForm, validForm)
  }
}

case class CreateUserForm(services: Option[String])

object CreateUserForm {

  val form: Form[CreateUserForm] = Form(
    mapping(
      "serviceSelection" -> optional(text).verifying(FormKeys.createServicesNoChoiceKey, selectedServices => selectedServices.isDefined)
    )(CreateUserForm.apply)(CreateUserForm.unapply)
  )
}
