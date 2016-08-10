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

package com.ramjetanvil.padrone.http.client.steam

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import com.ramjetanvil.padrone.AppConfig
import com.ramjetanvil.padrone.AppConfig._
import com.ramjetanvil.padrone.http.client.HttpClient.HttpClient
import com.ramjetanvil.padrone.http.client.Licensing.LicenseException
import com.typesafe.config.Config
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

object Client {


  case class Configuration(url: String, appId: String, key: String, developers: Set[SteamUserId])
  object Configuration {
    def fromAppConfig(config: Config): Configuration = {
      import collection.JavaConversions._
      Configuration(
        url = config.getString("url"),
        appId = config.getString("app-id"),
        key = config.getString("key"),
        developers = config.getStringList("developers").toSet.map(SteamUserId))
    }
  }

  class SteamHttpClient(httpClient: HttpClient, config: Configuration)(implicit ec: ExecutionContext) extends SteamClient {
    import com.ramjetanvil.padrone.http.client.HttpClient._
    val requests = new Requests()(config)

    override def fetchGameLicense(steamId: SteamUserId): Future[GameLicense] = {
      httpClient(requests.CheckAppOwnerShipV1(Map("steamid" -> steamId.value)))
        .unmarshallTo[JsValue]()
        .flatMap(responseJson => {
          val license = for {
            appOwnership <- (responseJson \ "appownership").toOption
            ownsApp <- (appOwnership \ "ownsapp").asOpt[Boolean].filter(_ == true)
            isPermanentLicense <- (appOwnership \ "permanent").asOpt[Boolean].orElse(Some(false))
          } yield GameLicense(steamId, isPermanentLicense)
          license match {
            case Some(l) => Future(l)
            case None => Future.failed(LicenseException(s"$steamId does not own app: ${config.appId}"))
          }
        })
    }

    override def authenticateUser(ticket: AuthSessionTicket): Future[SteamUserId] = {
      httpClient(requests.AuthenticateUserTicket(Map("ticket" → ticket.value)))
        .unmarshallTo[JsValue]()
        .flatMap(responseJson => {
          /* Example response:
             {"response":{"params":{"result":"OK",
                                    "steamid":"76561197979120212",
                                    "ownersteamid":"76561197979120212",
                                    "vacbanned":false,
                                    "publisherbanned":false}}}*/
          /* Example response:
             {"response":{"error":{"errorcode":101,
                                   "errordesc":"Invalid ticket"}}} */

          // TODO Handle user bans?
          /*
           val isUserBanned = {
              val isVacBanned = (payload \ "vacbanned").asOpt[Boolean].getOrElse(false)
              val isPublisherBanned = (payload \ "publisherbanned").asOpt[Boolean].getOrElse(false)
              isVacBanned || isPublisherBanned
            }
           */
          val steamId = for {
            payload <- (responseJson \ "response" \ "params").toOption
            _ <- (payload \ "result").asOpt[String].filter(_ == "OK")
            steamId <- (payload \ "steamid").asOpt[String]
          } yield steamId

          steamId match {
            case Some(userId) => Future(SteamUserId(userId))
            case _ => Future.failed(new Exception(s"Failed to authenticate user, ticket $ticket is invalid or user is banned"))
          }
        })
    }

    override def fetchUserDetails(steamUserId: SteamUserId): Future[SteamUserDetails] = {
      httpClient(requests.GetPlayerSummariesV2(Map("steamids" -> steamUserId.value)))
        .unmarshallTo[JsValue]()
        .map(responseJson => {
          /* Example response:
            {"response":{"players":[{
             "steamid":"76561197979120212",
             "communityvisibilitystate":3,
             "profilestate":1,
             "personaname":"Frank Versnel",
             "lastlogoff":1453898882,
             "profileurl":"http://steamcommunity.com/profiles/76561197979120212/",
             "avatar":"https://steamcdn-a.akamaihd.net/steamcommunity/public/images/avatars/06/0660d5ff2c9060fd1e46378d058e8cabc22bc0cc.jpg",
             "avatarmedium":"https://steamcdn-a.akamaihd.net/steamcommunity/public/images/avatars/06/0660d5ff2c9060fd1e46378d058e8cabc22bc0cc_medium.jpg",
             "avatarfull":"https://steamcdn-a.akamaihd.net/steamcommunity/public/images/avatars/06/0660d5ff2c9060fd1e46378d058e8cabc22bc0cc_full.jpg",
             "personastate":0,
             "primaryclanid":"103582791438615283",
             "timecreated":1131548717,
             "personastateflags":0}]}}*/
          val userDetails = for {
            players <- (responseJson \ "response" \ "players").asOpt[JsArray]
            playerProfile <- players.value.headOption
            playerName <- (playerProfile \ "personaname").asOpt[String]
          } yield SteamUserDetails(steamUserId, playerName, (playerProfile \ "avatarfull").asOpt[String])
          userDetails.getOrElse(SteamUserDetails(steamUserId, ""))
        })
    }

    class Requests(implicit config: Configuration) {
      val CheckAppOwnerShipV1: BuildSteamRequest =
        steamRequestSpec(
          "ISteamUser",
          "CheckAppOwnership",
          HttpMethods.GET,
          version = 1,
          requiredParams = Set("key", "steamid", "appid", "format"))
      val AuthenticateUserTicket: BuildSteamRequest =
        steamRequestSpec(
          "ISteamUserAuth",
          "AuthenticateUserTicket",
          HttpMethods.GET,
          version = 1,
          requiredParams = Set("key", "appid", "ticket", "format"))
      val GetPlayerSummariesV2: BuildSteamRequest =
        steamRequestSpec(
          "ISteamUser",
          "GetPlayerSummaries",
          HttpMethods.GET,
          version = 2,
          requiredParams = Set("key", "steamids"))

      type Path = String
      type Parameter = String
      type Parameters = Map[Parameter, String]
      type BuildSteamRequest = Parameters => HttpRequest

      private def steamRequestSpec(interface: String,
                           method: String,
                           httpMethod: HttpMethod,
                           version: Int,
                           requiredParams: Set[Parameter])
                          (implicit config: Configuration): BuildSteamRequest = {
        val defaultParams = Map[String, String](
          "appid" -> config.appId,
          "gameid" -> config.appId,
          "key" -> config.key,
          "format" -> "json")

        val versionUri = "v%04d".format(version)
        val url = Uri(s"${config.url}/$interface/$method/$versionUri")
        params => {
          val combinedParams = (defaultParams ++ params).filterKeys(requiredParams.contains)
          val headers = List[HttpHeader](Accept(`application/json`))
          httpMethod match {
            case GET => HttpRequest(GET, url.withQuery(Query(combinedParams)), headers)
            case POST => HttpRequest(POST, url, headers, FormData(combinedParams).toEntity)
          }
        }
      }
    }
  }
}
