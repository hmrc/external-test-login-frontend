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

package models

import models.UserTypes._

case class FieldDefinition(key: String, name: String, allowedUserTypes: Seq[UserType])

object FieldDefinitions {

  def getCtc(): Seq[FieldDefinition] = Seq(
    FieldDefinition("eoriNumber", "Economic Operator Registration and Identification (EORI) number", Seq(INDIVIDUAL, ORGANISATION)),
    FieldDefinition("userFullName", "Full Name", Seq(INDIVIDUAL, ORGANISATION)),
    FieldDefinition("emailAddress", "Email Address", Seq(INDIVIDUAL, ORGANISATION)),
    FieldDefinition("organisationDetails", "Organisation Details", Seq(ORGANISATION)),
    FieldDefinition("individualDetails", "Individual Details", Seq(INDIVIDUAL)),
    FieldDefinition("groupIdentifier", "Group Identifier", Seq(INDIVIDUAL, ORGANISATION))
  )
}
