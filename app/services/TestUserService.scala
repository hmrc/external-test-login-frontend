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
import models.UserTypes.{INDIVIDUAL, ORGANISATION, UserType}
import models.{Service, TestIndividual, TestOrganisation, TestUser}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TestUserService @Inject() (apiPlatformTestUserConnector: ApiPlatformTestUserConnector)(implicit ec: ExecutionContext) {

  def services(implicit hc: HeaderCarrier): Future[Seq[Service]] = apiPlatformTestUserConnector.getServices()

  def createUser(selectedService: String)(implicit hc: HeaderCarrier): Future[TestUser] =
    for {
      services <- apiPlatformTestUserConnector.getServices()
      testUser <- createUserWithServices(
        services.filter(
          x => x.key == selectedService
        )
      )
    } yield testUser

  private def createUserWithServices(services: Seq[Service])(implicit hc: HeaderCarrier): Future[TestIndividual] =
    apiPlatformTestUserConnector.createOrg(serviceKeysForUserType(ORGANISATION, services))

  private def serviceKeysForUserType(userType: UserType, services: Seq[Service]): Seq[String] =
    services
      .filter(
        s => s.allowedUserTypes.contains(userType)
      )
      .map(
        s => s.key
      )
}
