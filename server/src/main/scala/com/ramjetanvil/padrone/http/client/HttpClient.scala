/*
 * MIT License
 *
 * Copyright (c) 2016 Ramjet Anvil
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.ramjetanvil.padrone.http.client

import java.io.{ByteArrayInputStream, IOException}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.util.FastFuture
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object HttpClient {

  type HttpClient = HttpRequest => Future[HttpResponse]

  implicit val system = ActorSystem("http-client")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  /**
   * Create an HTTP client
   *
   * @return an HTTP client
   */
  def createHttpClient(): HttpClient = {
    val client = Http()
    request => client.singleRequest(request)
  }

  implicit class HttpClientExtensions(httpClient: HttpClient) {
    import akka.http.scaladsl.model.HttpMethods._

    def get[T](url: String,
               fields: Map[String, String] = Map.empty,
               headers: List[HttpHeader] = List.empty)
              (implicit um: Unmarshaller[HttpResponse, T]): Future[HttpResponse] = {
      httpClient(HttpRequest(GET, Uri(url).withQuery(Query(fields)), headers))
    }

    def post[T](url: String, content: T, headers: List[HttpHeader] = List.empty)
               (implicit marshaller: Marshaller[T, RequestEntity]): Future[HttpResponse] = {
      Marshal(content).to[RequestEntity]
        .flatMap(requestEntity => httpClient(HttpRequest(POST, Uri(url), headers, entity = requestEntity)))
    }

    def addLogging(implicit logger: Logger): HttpClient = { request =>
      logger.info(s"Performing HTTP request $request")
      httpClient(request)
        .andThen {
          case Success(response) => logger.info(s"HTTP response $response")
          case Failure(exception) => logger.error(s"Failed to receive response $exception")
        }
    }
  }

  implicit class UnmarshallerExtensions[A](unmarshallable: Future[A]) {
    def unmarshallTo[B]()(implicit um: Unmarshaller[A, B]): Future[B] = {
      unmarshallable.flatMap(rawData => {
        Unmarshal[A](rawData).to[B]
      })
    }
  }

//  implicit def playJsValueUnmarshaller(implicit fm: Materializer): FromEntityUnmarshaller[JsValue] =
//    Unmarshaller.byteStringUnmarshaller
//      .forContentTypes(`application/json`)
//      .mapWithCharset { (data, charset) =>
//        val input = if (charset == `UTF-8`) data.utf8String else data.decodeString(charset.nioCharset.name)
//        Json.parse(input)
//      }
}
