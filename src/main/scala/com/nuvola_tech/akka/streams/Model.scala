/*
 * Copyright 2019 NuvolaTech LLC - Roberto Congiu
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

package com.nuvola_tech.akka.streams

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import spray.json.DefaultJsonProtocol

object Model {

  final case class Id(id:String)
  final case class Response(value:Int)

  // collect your json format instances into a support trait:
  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val itemFormat = jsonFormat1(Response)
  }

  object DatabaseBusyException extends Exception
  object TooManyRequestsException extends Exception
  case class DatabaseUnexpectedException(code:StatusCode) extends Exception
  case object StreamFailedAfterMaxRetriesException extends Exception
  case class StreamFailedAfterMaxRetriesExceptionForId(id:Id) extends Exception {
    override def toString: String = s"for ID: ${id} " + super.toString
  }


}
