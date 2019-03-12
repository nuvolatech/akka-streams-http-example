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


import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.stream.scaladsl.{Keep, RestartSource, Sink, Source}
import com.nuvola_tech.akka.streams.Model._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


object BackPressureTest  extends JsonSupport {
  lazy implicit val system = ActorSystem("QuickStart")
  lazy implicit val materializer = ActorMaterializer()
  lazy implicit val ec = system.dispatcher


  val tries: scala.collection.mutable.HashMap[String, Int] = new mutable.HashMap()

  // use a superpool vs SingleRequest: connection pooling, automatic keepalive, etc.
  val pool = Http().superPool[Id]()

  // this is used to store how long each request took - very simple performance evaluation
  // you won't need this part in production, since you'll probably be using some monitoring tool.
  var times = Seq[Long]()
  def timedFuture[T](future: Future[T]) = {
    val start = System.currentTimeMillis()
    future.onComplete({
      case _ => times = (System.currentTimeMillis() - start) +: times
    })
    future
  }

  // A shared kill switch used to terminate the *entire stream* if needed
  val killSwitch = KillSwitches.shared("ks")

  def getResponse(id: Id): Future[Model.Response] = timedFuture {
    RestartSource.withBackoff(
      minBackoff = 20.milliseconds,
      maxBackoff = 30.seconds,
      randomFactor = 0.2,
      maxRestarts = 10
    ) { () =>
        val request = HttpRequest(uri = s"http://localhost:8080/${id.id}")
        // keep tracks of the number of tries for one UUID, uncomment for debugging/testing
      tries.put(id.id, tries.getOrElse(id.id, 0) + 1)
        Source.single((request, id)).via(pool)
          .mapAsync(parallelism = 1)(handleResponse)
      }.runWith(Sink.head).recover {
        case _ => throw StreamFailedAfterMaxRetriesExceptionForId(id)
      }
  }

  /**
    * Partial function that handles the response from the server.
    * YOU SHOULD CHANGE YOUR LOGIC TO FIT YOUR NEEDS.
    *
    * What this particular logic does is:
    * * in case of 429 Too Many Requests, it applies exponential backoff
    * * in all other error cases, it uses the kill switch to terminate the stream.
    */
  val handleResponse:PartialFunction[(Try[HttpResponse],Id),Future[Response]]  =  {
      // we can connect, and we get a valid response. Response however could be 500, etc.
    case (Success(s), _) => s match {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[Response]
      case HttpResponse(StatusCodes.TooManyRequests,_,entity,_) =>
        entity.discardBytes()
        throw TooManyRequestsException
      case HttpResponse(statusCode, _, entity, _) =>
        entity.discardBytes()
        killSwitch.shutdown()
        throw DatabaseUnexpectedException(statusCode)
    }
    // something went wrong, can't connect.
    case (Failure(f), _) => throw f
  }


  /**
    * Entry point for executable.
    * Launches the test server and streams to it some valid random data
    * @param args
    */
  def main(args: Array[String]): Unit = {
    // server will return TooManyRequest with a probability of 20%.
    // this will cause the stream to retry sending the data.
    // Try changing the error code to InternalServerError, instead
    // of retrying, the stream will terminate.
    val server = new TestHttpServer(0.3D, StatusCodes.TooManyRequests)
    //val server = new TestHttpServer(0.1D, StatusCodes.InternalServerError)

    // generate random data
    val ids = 1.to(10).map { _ => Id(UUID.randomUUID().toString) }

    // create the stream, using the killSwitch.
    val aggregate = Source(ids) // the data source
      .viaMat(killSwitch.flow)(Keep.right)
      .mapAsync(parallelism = 4)(getResponse) // up to 4 parallel connections to HTTP server
      .map(_.value)
      .runWith(Sink.fold(0)(_ + _)) // we calculate the sum. Should be equal to ids.length

    aggregate.onComplete {
      case Success(sum) => println(s"Sum : $sum", tries); terminate()
      case Failure(error) => println(s"Stream something wrong : $error" + tries.toString()); terminate()
    }


    def terminate() = {
      println(s"Execution time avg: ${times.reduce(_ + _)/times.length}: ${times}")
      println(s"Tries: ${tries}")
      for {
        // terminate web server
        _ <- server.bindingFuture.map { x => x.terminate(10.seconds) }
        // terminate connection pools
        _ <- Http().shutdownAllConnectionPools()
        // finally, terminate actor system
        _ <- system.terminate()
      } yield {
        println("all terminated")
      }
    }
  }
}





