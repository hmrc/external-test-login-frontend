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

package services

import connectors.ApiPlatformTestUserConnector
import helpers.AsyncHmrcSpec
import models.{Field, Service, TestIndividual, TestOrganisation}
import models.UserTypes.{AGENT, INDIVIDUAL, ORGANISATION}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class TestUserServiceSpec extends AsyncHmrcSpec {

  private val service1 = "service1"
  private val service2 = "service2"
  private val service3 = "service3"
  private val service4 = "service4"

  private val services = Seq(
    Service(service1, "Service 1", Seq(INDIVIDUAL)),
    Service(service2, "Service 2", Seq(INDIVIDUAL, ORGANISATION)),
    Service(service3, "Service 3", Seq(ORGANISATION)),
    Service(service4, "Service 4", Seq(AGENT))
  )

  trait Setup {
    implicit val hc                      = HeaderCarrier()
    val mockApiPlatformTestUserConnector = mock[ApiPlatformTestUserConnector]
    val underTest                        = new TestUserService(mockApiPlatformTestUserConnector)

    when(mockApiPlatformTestUserConnector.getServices()(*)).thenReturn(successful(services))
  }

  "createUser" should {
    "return a generated organisation when type is ORGANISATION" in new Setup {
      val organisation = TestIndividual("org-user",
                                        "org-password",
                                        Seq(Field("saUtr", "Self Assessment UTR", "1555369053"))
      ) // TODO - why does this only work  with TestIndividual?

      when(mockApiPlatformTestUserConnector.createOrg(eqTo(Seq(service3)))(*)).thenReturn(successful(organisation))

      val result = await(underTest.createUser("service3"))

      result shouldBe organisation
    }
  }

}
