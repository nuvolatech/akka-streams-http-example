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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.util.Random

/**
  * Test webserver, that randomly fails on requests.
  * Instantiate with failure probability, from 0 to 1.
  * With 0 it never fails, with 1 it always fails.
  */
class TestHttpServer(failProbability:Double = 0.5, statusCode:StatusCode = StatusCodes.InternalServerError)(implicit system:ActorSystem)  {
  implicit lazy val materializer =  ActorMaterializer()
  implicit lazy val executionContext = system.dispatcher

  val random = Random

  def body(value: Int): String = s"""{"value":$value}"""

  val Id = """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""".r

  val route =
    path(Id) { id =>
      get {
        if (random.nextDouble() < failProbability) {
          println("Return error")
          complete(statusCode)
        }
        else {
          println("Return ok")
          complete(
            StatusCodes.OK,
            HttpEntity(ContentTypes.`application/json`,
              body(1))
          )
        }
      }
    }

  println("Starting HTTP server")
  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

}