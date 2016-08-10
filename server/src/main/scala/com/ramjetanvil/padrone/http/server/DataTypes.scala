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

package com.ramjetanvil.padrone.http.server

import com.ramjetanvil.padrone.domain.MasterServerQueryLayer._
import com.ramjetanvil.padrone.http.client.Licensing.PlayerId
import com.ramjetanvil.padrone.http.client.itch.DownloadKey
import com.ramjetanvil.padrone.util.IpEndpoint

object DataTypes {

  object GameCommunicationProtocol {
    sealed trait ClientMessage
    case class FindHostRequest(hostName: String) extends ClientMessage
    case class HostRegistrationRequest(hostName: HostName,
                                       peerInfo: PeerInfo,
                                       password: Option[String],
                                       isPrivate: Boolean,
                                       version: GameVersion,
                                       maxPlayers: Int) extends ClientMessage {
      def externalEndpoint: IpEndpoint = peerInfo.external
    }
    case class PingRequest(hostEndpoint: IpEndpoint, connectedClients: Set[ClientSessionId]) extends ClientMessage
    case class JoinRequest(hostEndpoint: IpEndpoint, password: Option[String]) extends ClientMessage
    case class ReportLeaveRequest(hostEndpoint: IpEndpoint, sessionId: ClientSessionId) extends ClientMessage

    sealed trait ServerMessage
    case class JoinResponse(sessionId: ClientSessionId, secret: ClientSecret)

    sealed case class ErrorMessage(messageType: String, message: String)
    object ErrorMessages {
      def hostNameUnavailable(hostName: HostName) = ErrorMessage("HostNameUnavailable", s"Host name '${hostName.value}' is unavailable")
    }
  }

  case class RenderableAppState(hosts: Iterable[Host])

  def toRenderableAppState(db: MasterServerDb): RenderableAppState = {
    RenderableAppState(db.hosts.values)
  }

  case class Connection(from: IpEndpoint, to: IpEndpoint)
  case class Error(reason: String)

  case class PlayerInfo(name: Option[String] = None, avatarUrl: Option[String] = None,
                        isAdmin: Boolean = false, isDeveloper: Boolean = false)
  object PlayerInfo {
    val Empty = PlayerInfo()
  }
  case class PlayerSessionInfo(sessionId: ClientSessionId, secret: ClientSecret, playerInfo: PlayerInfo)

  // TODO Add support for players that own multiple licenses of the same game on the same user id
  case class Player(id: PlayerId, info: PlayerInfo = PlayerInfo.Empty) {
    def isAdmin = info.isAdmin
    override def toString: String = s"Player('${info.name.getOrElse("unknown")}', $id)"
  }
}