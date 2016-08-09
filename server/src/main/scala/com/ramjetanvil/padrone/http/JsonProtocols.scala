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

package com.ramjetanvil.padrone.http

import java.net.InetAddress
import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.common.net.InetAddresses
import com.ramjetanvil.padrone.http.client.itch.JsonProtocol
import com.ramjetanvil.padrone.http.client.steam.AuthSessionTicket
import com.ramjetanvil.padrone.util.IpEndpoint
import com.ramjetanvil.padrone.util.geo.GeoCoords
import com.ramjetanvil.padrone.domain.MasterServerQueryLayer._
import com.ramjetanvil.padrone.domain.PasswordVerification.BCryptHash
import com.ramjetanvil.padrone.http.client.itch
import com.ramjetanvil.padrone.http.client.steam.AuthSessionTicket
import com.ramjetanvil.padrone.http.server.DataTypes.GameCommunicationProtocol._
import com.ramjetanvil.padrone.http.server.DataTypes._
import com.ramjetanvil.padrone.util.UnitOfMeasure.{KiloMeters, Meters}
import com.ramjetanvil.padrone.util.{IpEndpoint, JsonUtil}
import com.ramjetanvil.padrone.util.geo.GeoCoords
import com.ramjetanvil.padrone.util.geo.GeoDb.{Country, Location}
import com.ramjetanvil.padrone.http.client.itch.JsonProtocol
import com.ramjetanvil.padrone.http.client.steam.AuthSessionTicket
import com.ramjetanvil.padrone.util.IpEndpoint
import com.ramjetanvil.padrone.util.geo.GeoCoords
import spray.json._

import scala.util.{Failure, Success, Try}

object JsonProtocols extends SprayJsonSupport with DefaultJsonProtocol {

  import com.ramjetanvil.padrone.util.JsonUtil._

  implicit object JsonInetAddressFormat extends JsonFormat[InetAddress] {
    override def write(inetAddress: InetAddress): JsValue = {
      JsString(inetAddress.getHostAddress)
    }

    override def read(json: JsValue): InetAddress = {
      json match {
        case JsString(ipAddress) =>
          Try(InetAddresses.forString(ipAddress)) match {
            case Success(inetAddress) => inetAddress
            case Failure(e) => deserializationError(s"$ipAddress is not a valid IP address", e)
          }
        case jsValue =>
          deserializationError(s"IP address string expected but got: $jsValue")
      }
    }
  }

  /**
   * Converts to and from '2007-12-03T10:15:30.00Z'-like date string representations
   */
  implicit object JsonInstantFormat extends JsonFormat[Instant] {
    override def read(json: JsValue): Instant = {
      json match {
        case JsString(dateTimeString) =>
          Try(Instant.parse(dateTimeString)) match {
            case Success(dateTime) => dateTime
            case Failure(e) => deserializationError(s"Error while parsing $dateTimeString as Instant.", e)
          }
        case jsValue =>
          deserializationError(s"Date time string expected but got $jsValue")
      }
    }

    override def write(dateTime: Instant): JsValue = {
      JsString(dateTime.toString)
    }
  }

  implicit val JsonMetersFormat = singleValueFormat[Meters, Double](Meters.apply, _.value)
  implicit val JsonKiloMetersFormat = singleValueFormat[KiloMeters, Double](KiloMeters.apply, _.value)

  implicit val JsonPlayerInfo = jsonFormat4(PlayerInfo.apply)
  implicit val JsonPlayer = new JsonFormat[Player] {
    override def write(player: Player): JsValue = JsString(player.toString)
    override def read(json: JsValue): Player = deserializationError("Cannot deserialize a player")
  }

  implicit val JsonIpEndpointFormat = jsonFormat2(IpEndpoint.apply)
  implicit val JsonCountryFormat = singleValueFormat[Country, String](Country.apply, _.isoCode)
  implicit def JsonGeoCoordsFormat = jsonFormat2(GeoCoords.apply)
  implicit val JsonLocationFormat = jsonFormat2(Location)
  implicit val JsonPeerInfoFormat = jsonFormat2(PeerInfo)
  implicit val JsonHostNameFormat = singleValueFormat[HostName, String](HostName.apply, _.value)
  implicit val JsonGameVersionFormat = singleValueFormat[GameVersion, String](GameVersion.apply, _.value)
  implicit val JsonClientSessionIdFormat = singleValueFormat[ClientSessionId, String](ClientSessionId.apply, _.value)
  implicit val JsonClientSecretFormat = singleValueFormat[ClientSecret, String](ClientSecret.apply, _.value)
  implicit val JsonClientStateFormat = jsonFormat5(ClientState)
  implicit val JsonPlayerSessionInfo = jsonFormat3(PlayerSessionInfo)
  // This is a hack to simply skip serialization of BCrypt hashes because
  // we probably never want to serialize them with Json
  implicit object JsonBCryptHashFormat extends JsonFormat[BCryptHash] {
    override def write(obj: BCryptHash): JsValue = JsNull
    override def read(json: JsValue): BCryptHash = deserializationError("Cannot deserialize a BCrypt hash")
  }
  implicit val JsonHostFormat = jsonFormat11(Host.apply)
  implicit val JsonRemoteHostFormat = jsonFormat9(RemoteHost)
  implicit val JsonErrorFormat = jsonFormat1(Error)
  implicit val JsonConnectionFormat = jsonFormat2(Connection)

  // Steam
  implicit val JsonSteamAuthSessionTicket = singleValueFormat[AuthSessionTicket, String](
    AuthSessionTicket.apply, _.value)

  // Itch IO
  implicit val JsonItchDownloadKey = JsonProtocol.JsonDownloadKeyFormat

  // Renderable state
  implicit val JsonRenderableAppState = jsonFormat1(RenderableAppState.apply)



  object GameCommunication {

    type MessageType = String

    def messageFormat[T](extractMessageType: T => MessageType,
                         jsonFormats: Map[MessageType, RootJsonFormat[T]]): RootJsonFormat[T] = new RootJsonFormat[T] {

      override def read(json: JsValue): T = {
        json match {
          case jsObject: JsObject => {
            jsObject.fields.get("messageType") match {
              case Some(messageType: JsString) => {
                jsonFormats.get(messageType.value) match {
                  case Some(jsonFormat) => {
                    val strippedJsObject = jsObject.copy(fields = jsObject.fields - "messageType")
                    jsonFormat.read(strippedJsObject)
                  }
                  case _ => deserializationError(s"Message type $messageType is unsupported")
                }
              }
              case _ => deserializationError(s"Message type could not be found for web.js object: $jsObject")
            }
          }
          case jsValue => deserializationError(s"Expected JsObject but got $jsValue")
        }
      }

      override def write(obj: T): JsValue = {
        val messageType = extractMessageType(obj)
        jsonFormats.get(messageType) match {
          case Some(jsonFormat) => {
            JsObject(
              jsonFormat.write(obj).asJsObject.fields ++ Map("messageType" -> JsString(messageType))
            )
          }
          case _ => serializationError(s"Unsupported message type $messageType for object $obj")
        }
      }
    }

    // Client messages
    implicit val JsonPeerRegistrationRequestFormat = jsonFormat6(HostRegistrationRequest)
    implicit val JsonPingRequestFormat = jsonFormat2(PingRequest)
    implicit val JsonJoinRequestFormat = jsonFormat2(JoinRequest)
    implicit val JsonJoinResponseFormat = jsonFormat2(JoinResponse)
    implicit val JsonReportLeaveRequestFormat = jsonFormat2(ReportLeaveRequest)
    implicit val JsonErrorMessageFormat = jsonFormat2(ErrorMessage)

    // Master server messages
//    implicit val JsonMasterServerMessageFormat = messageFormat[MasterServerMessage](
//      extractMessageType = {
//        case HostAdded(_) => "hostAdded"
//        case HostRemoved(_) => "hostRemoved"
//        case HostList(_) => "hostList"
//      },
//      jsonFormats = Map[String, RootJsonFormat[MasterServerMessage]](
//        "hostAdded" -> jsonFormat1(HostAdded).asInstanceOf[RootJsonFormat[MasterServerMessage]],
//        "hostRemoved" -> jsonFormat1(HostRemoved).asInstanceOf[RootJsonFormat[MasterServerMessage]],
//        "hostList" -> jsonFormat1(HostList).asInstanceOf[RootJsonFormat[MasterServerMessage]]
//      )
//    )
  }


}