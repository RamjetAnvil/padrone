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

package com.ramjetanvil.padrone.http.client.oculus

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, HttpRequest, Uri}
import com.ramjetanvil.padrone.http.client.HttpClient.HttpClient
import com.ramjetanvil.padrone.http.client.oculus.JsonProtocol._
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}

object Client {

  case class Configuration(baseUrl: String, appId: String, appSecret: String)
  object Configuration {
    def fromAppConfig(config: Config): Configuration = {
      Configuration(
        baseUrl = config.getString("url"),
        appId = config.getString("app-id"),
        appSecret = config.getString("app-secret"))
    }
  }

  class OculusHttpClient(httpClient: HttpClient, config: Configuration)
                        (implicit ec: ExecutionContext) extends OculusClient {
    import com.ramjetanvil.padrone.http.client.HttpClient._

    val baseUrl = config.baseUrl
    //val appUrl = s"${baseUrl}/${config.appId}"
    val appAccessToken = s"OC|${config.appId}|${config.appSecret}"

    override def fetchUserId(userAccessToken: UserAccessToken): Future[OculusUserId] = {
      val request = HttpRequest(
        GET,
        Uri(s"$baseUrl/me").withQuery(Query(("access_token", userAccessToken.value))),
        headers = List(Accept(`application/json`)))
      httpClient(request)
        .unmarshallTo[UserId]()
        .flatMap { case UserId(id) =>
          id match {
            case Some(userId) => Future(OculusUserId(userId))
            case _ => Future.failed(new Exception(s"Invalid $userAccessToken"))
          }
        }
    }

    override def authenticateUser(userId: OculusUserId, nonce: Nonce): Future[OculusUserId] = {
      val request = HttpRequest(
        POST,
        Uri(s"$baseUrl/user_nonce_validate").withQuery(Query(
          ("access_token", appAccessToken),
          ("nonce", nonce.value),
          ("user_id", userId.value))),
        headers = List(Accept(`application/json`)))
      httpClient(request)
        .unmarshallTo[AuthenticationResult]()
        .flatMap { case AuthenticationResult(userOwnsGame) =>
          if(userOwnsGame.getOrElse(false)) {
            Future(userId)
          } else {
            Future.failed(new Exception(s"Failed to authenticate user $userId, " +
              s"user id or nonce $nonce is invalid or user is banned"))
          }
        }
    }
  }

}
