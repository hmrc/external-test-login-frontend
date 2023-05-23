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
import connectors.ApiPlatformTestUserConnector
import helpers.AsyncHmrcSpec
import logger.ApplicationLogger
import models.UserTypes.{INDIVIDUAL, ORGANISATION}
import models.{Field, FieldDefinition, Service, TestOrganisation, UserTypes}
import org.jsoup.nodes.Document
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, AnyContentAsFormUrlEncoded, MessagesControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.TestUserService
import views.html.{CreateTestUserViewGeneric, TestUserViewGeneric}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import scala.jdk.CollectionConverters._

class TestUserControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with LogSuppressing with ApplicationLogger {

  private val organisationFields = Seq(
    Field("saUtr", "Self Assessment UTR", "1555369053"),
    Field("empRef", "Employer Ref", "555/EIA000"),
    Field("ctUtr", "CT UTR", "1555369054"),
    Field("vrn", "", "999902541")
  )
  val organisation = TestOrganisation("org-user", "org-password", organisationFields)

  trait Setup {
    implicit val materializer = app.injector.instanceOf[Materializer]
    private val csrfAddToken  = app.injector.instanceOf[play.filters.csrf.CSRFAddToken]

    val config: FrontendAppConfig = mock[FrontendAppConfig]

    val fieldDefinitions                 = Seq(FieldDefinition("fieldDef1", "Field Def 1", Seq(INDIVIDUAL, ORGANISATION)))
    val mcc                              = app.injector.instanceOf[MessagesControllerComponents]
    val createTestUserViewGeneric        = app.injector.instanceOf[CreateTestUserViewGeneric]
    val testUserViewGeneric              = app.injector.instanceOf[TestUserViewGeneric]
    val mockTestUserService              = mock[TestUserService]
    val mockApiPlatformTestUserConnector = mock[ApiPlatformTestUserConnector]
    val svc                              = Seq(Service("ctc", "common-transit-convention-traders", Seq(UserTypes.ORGANISATION)))
    val serviceKey                       = "common-transit-convention-traders"

    val routingUrls = Seq(
      "common-transit-convention-traders, http://localhost:9619/api-test-login/sign-in?continue=http://localhost:9485/manage-transit-movements"
    )

    implicit val appConfig = config

    val underTest = new TestUserController(
      app.injector.instanceOf[MessagesApi],
      mockTestUserService,
      mockApiPlatformTestUserConnector,
      mcc,
      createTestUserViewGeneric,
      testUserViewGeneric
    )

    when(mockTestUserService.services(*)).thenReturn(successful(svc))
    when(config.serviceKey).thenReturn(serviceKey)
    when(mockTestUserService.createUser(eqTo(serviceKey))(*)).thenReturn(successful(organisation))

    def elementExistsById(doc: Document, id: String): Boolean = doc.select(s"#$id").asScala.nonEmpty

    def execute[T <: play.api.mvc.AnyContent](action: Action[AnyContent], request: FakeRequest[T] = FakeRequest()) =
      csrfAddToken(action)(request)
  }

  "showCreateTestUser" should {
    "display the Create test user page" in new Setup {
      val result = execute(underTest.showCreateUserPageGeneric())
      val page   = contentAsString(result)

      page should include("Common Transit Convention")
      page should include("You can create a test user to manage movements in the sandbox environment")
      page should include("If you already have a test user you can login to CTC")

      // TODO add more coverage
    }
  }

  "createOrg" should {

    "create an organisation" in new Setup {
      val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest().withFormUrlEncodedBody(("serviceSelection", "common-transit-convention-traders"))

      val result = execute(underTest.createUserGeneric(), request)

      contentAsString(result) should include(organisation.userId)
    }
  }
}
