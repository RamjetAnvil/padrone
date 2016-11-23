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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.scalapenos.spray.LowercaseSprayJsonSupport
import spray.json.{JsonFormat, RootJsonFormat}

object JsonProtocol extends SprayJsonSupport with LowercaseSprayJsonSupport {

  case class Response[T](response: T)

  /* Example response:
   {"response":{"error":{"errorcode":101,
                         "errordesc":"Invalid ticket"}}} */
  case class Error(error: ErrorDescription)
  case class ErrorDescription(errorCode: Int, errorDesc: String)

  /* Example response:
   {"response":{"params":{"result":"OK",
                          "steamid":"76561197979120212",
                          "ownersteamid":"76561197979120212",
                          "vacbanned":false,
                          "publisherbanned":false}}}*/
  case class AuthResult(params: AuthParameters)
  case class AuthParameters(result: Option[String], steamId: Option[String])

  case class AppOwnershipWrapper(appOwnership: Option[AppOwnership])
  case class AppOwnership(ownsApp: Option[Boolean], permanent: Option[Boolean])

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
  case class Players(players: Seq[PlayerDetails])
  case class PlayerDetails(personaName: Option[String], avatarFull: Option[String])

  implicit def JsonResponseFormat[T](implicit format: JsonFormat[T]): RootJsonFormat[Response[T]] = {
    jsonFormat1(Response[T])
  }

  implicit val JsonAppOwnershipFormat = jsonFormat2(AppOwnership)
  implicit val JsonAppOwnershipWrapperFormat = jsonFormat1(AppOwnershipWrapper)

  implicit val JsonErrorDescriptionFormat = jsonFormat2(ErrorDescription)
  implicit val JsonErrorFormat = jsonFormat1(Error)

  implicit val JsonAuthParametersFormat = jsonFormat2(AuthParameters)
  implicit val JsonAuthSuccessFormat = jsonFormat1(AuthResult)

  implicit val JsonPlayerDetailsFormat = jsonFormat2(PlayerDetails)
  implicit val JsonPlayersFormat = jsonFormat1(Players)
}
