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

package com.ramjetanvil.padrone.http.client.itch

import akka.stream.Materializer
import com.typesafe.config.Config
import com.ramjetanvil.padrone.http.client.HttpClient._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.{Accept, RawHeader}
import akka.http.scaladsl.model._
import HttpMethods._
import com.ramjetanvil.padrone.http.client.HttpClient._
import JsonProtocol._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import spray.json.{JsonReader, RootJsonFormat}

import scala.concurrent.{ExecutionContext, Future}

object Client {

  case class Configuration(url: String, apiKey: String, gameId: GameId, developers: Set[UserId])
  object Configuration {
    def fromAppConfig(config: Config): Configuration = {
      import collection.JavaConverters._
      Configuration(
        url = config.getString("url"),
        apiKey = config.getString("api-key"),
        gameId = GameId(BigInt(config.getString("game-id"))),
        developers = config.getStringList("developers").asScala.toSet.map(UserId.fromString))
    }
  }

  class ItchIoHttpClient(httpClient: HttpClient, config: Configuration)
                        (implicit fm: Materializer, ec: ExecutionContext) extends ItchIoClient {
    val apiVersion = 1
    val apiUrl = s"${config.url}/$apiVersion"
    val baseUrl = s"$apiUrl/${config.apiKey}"
    val gameUrl = s"$baseUrl/game/${config.gameId.value}"

    override val gameId: GameId = config.gameId

    override def fetchUserDetails(userApiKey: UserApiKey): Future[UserDetails] = {
      val request = HttpRequest(GET, Uri(s"$apiUrl/jwt/me"), headers = List(RawHeader("Authorization", userApiKey.value)))
      performRequest[UserDetails](request)
    }

    override def fetchMyGames(): Future[Games] = {
      val request = HttpRequest(GET, Uri(s"$baseUrl/my-games"))
      performRequest[Games](request)
    }

    override def fetchPurchases(userId: UserId): Future[Purchases] = {
      val request = HttpRequest(
        GET,
        Uri(s"$gameUrl/purchases").withQuery(Query(("user_id", userId.value.toString))))
      performRequest[Purchases](request)
    }

    override def fetchPurchasesByEmail(emailAddress: String): Future[Purchases] = {
      val request = HttpRequest(
        GET,
        Uri(s"$gameUrl/purchases").withQuery(Query(("email", emailAddress))))
      performRequest[Purchases](request)
    }

    override def fetchDownloadKeyInfo(downloadKey: DownloadKey): Future[DownloadKeyInfo] = {
      val request = HttpRequest(
        GET,
        Uri(s"$gameUrl/download_keys").withQuery(Query(("download_key", downloadKey.value))))
      performRequest[DownloadKeyInfo](request)
    }

    override def fetchDownloadKeyInfoByUserId(userId: UserId): Future[DownloadKeyInfo] = {
      val request = HttpRequest(
        GET,
        Uri(s"$gameUrl/download_keys").withQuery(Query(("user_id", userId.value.toString))))
      performRequest[DownloadKeyInfo](request)
    }

    override def fetchDownloadKeyInfoByEmail(emailAddress: String): Future[DownloadKeyInfo] = {
      val request = HttpRequest(
        GET,
        Uri(s"$gameUrl/download_keys").withQuery(Query(("email", emailAddress))))
      performRequest[DownloadKeyInfo](request)
    }

    private def performRequest[T](request: HttpRequest)(implicit json: RootJsonFormat[T]): Future[T] = {
      val headers = request.headers.+:(Accept(`application/json`))
      httpClient(request.withHeaders(headers))
        .unmarshallTo[Either[Errors, T]]()
        .flatMap {
          case Right(payload) => Future(payload)
          case Left(errors) => Future.failed(ItchIoException(errors.toString))
        }
    }
  }


}
