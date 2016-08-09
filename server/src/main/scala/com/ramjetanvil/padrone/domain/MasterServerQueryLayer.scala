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

package com.ramjetanvil.padrone.domain

import java.net.InetAddress
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

import com.ramjetanvil.padrone.domain.PasswordVerification.{BCryptHash, BCryptHash$}
import com.ramjetanvil.padrone.http.server.DataTypes.{Player, PlayerSessionInfo}
import com.ramjetanvil.padrone.util.IpEndpoint
import com.ramjetanvil.padrone.util.UnitOfMeasure.KiloMeters
import com.ramjetanvil.padrone.http.client.Licensing.PlayerId
import com.ramjetanvil.padrone.http.server.DataTypes.{Player, PlayerInfo, PlayerSessionInfo}
import com.ramjetanvil.padrone.util.IpEndpoint
import com.ramjetanvil.padrone.util.UnitOfMeasure._
import com.ramjetanvil.padrone.util.geo.GeoDb._
import com.ramjetanvil.padrone.http.server.DataTypes.{Player, PlayerSessionInfo}
import com.ramjetanvil.padrone.util.IpEndpoint
import com.ramjetanvil.padrone.util.UnitOfMeasure.KiloMeters

object MasterServerQueryLayer {

  case class PeerInfo(externalEndpoint: IpEndpoint, internalEndpoint: IpEndpoint) {
    def contains(ipEndpoint: IpEndpoint): Boolean = {
      ipEndpoint == externalEndpoint || ipEndpoint == internalEndpoint
    }
    override def toString: String = s"($externalEndpoint,$internalEndpoint)"
  }
  case class HostName(value: String) extends AnyVal
  case class GameVersion(value: String) extends AnyVal
  case class ClientSessionId(value: String) extends AnyVal
  object ClientSessionId {
    def generate() = ClientSessionId(UUID.randomUUID().toString)
  }
  case class ClientSecret(value: String) extends AnyVal
  object ClientSecret {
    def generate() = ClientSecret(UUID.randomUUID().toString)
  }

  case class Host(endpoint: PeerInfo, name: HostName, password: Option[BCryptHash], isPrivate: Boolean,
                  location: Option[Location], hostingPlayer: Player, version: GameVersion, registeredAt: Instant,
                  lastPingReceived: Instant, maxPlayers: Int, clients: Map[ClientSessionId, ClientState] = Map.empty) {
    def playerCount = clients.size + 1
    def isFull = playerCount >= maxPlayers
    def isPasswordProtected = password.isDefined
    def externalEndpoint = endpoint.externalEndpoint
  }

  case class ClientState(player: Player, joinedIp: IpEndpoint,
                         sessionId: ClientSessionId, secret: ClientSecret, joinTimestamp: Instant)
  case class MasterServerDb(hosts: Map[IpEndpoint, Host],
                            clients: Map[PlayerId, ClientState])

  object MasterServerDb {
    val initialValue: MasterServerDb = MasterServerDb(hosts = Map.empty, clients = Map.empty)
  }

  implicit class PeerInfoExtensions(peerInfo: PeerInfo) {
    def location(implicit locationDb: LocationDb): Option[Location] = {
      locationDb(peerInfo.externalEndpoint.address).toOption
    }
  }

  case class RemoteHost(name: HostName, hostedBy: Option[String], peerInfo: PeerInfo, isPasswordProtected: Boolean,
                        onlineSince: Instant, distanceInKm: Option[KiloMeters], country: Option[String],
                        version: GameVersion, playerCount: Int, maxPlayers: Int)

  implicit class MasterServerDbView(db: MasterServerDb) {

    private def asRemoteHost(host: Host, distance: Option[KiloMeters]): RemoteHost = {
      RemoteHost(
        host.name,
        host.hostingPlayer.info.name,
        host.endpoint,
        isPasswordProtected = host.isPasswordProtected,
        onlineSince = host.registeredAt,
        distanceInKm = distance,
        country = host.location.map(_.country.name),
        version = host.version,
        playerCount = host.playerCount,
        maxPlayers = host.maxPlayers)
    }

    // TODO Sorting hosts is incredibly slow once we have lots of registered hosts
    //      We need a more efficient sorting algorithm
    def listHosts(version: GameVersion, hideFull: Boolean, hidePasswordProtected: Boolean,
                  peerAddress: Option[InetAddress] = None)
                 (implicit locationDb: LocationDb): Seq[RemoteHost] = {
      val peerLocation = peerAddress.flatMap(locationDb(_).toOption)
      val hosts = db.hosts.values
        .filter { host =>
          !host.isPrivate &&
          !(host.isPasswordProtected && hidePasswordProtected) &&
          !(host.isFull && hideFull) &&
          host.version == version
        }
        .map { hostRegistration =>
          val distance = for {
            Location(peerCoordinates, _) <- peerLocation
            Location(hostCoordinates, _) <- hostRegistration.location
          } yield peerCoordinates.distanceTo(hostCoordinates)
          asRemoteHost(hostRegistration, distance.map(_.toKiloMeters))
        }
      hosts.toSeq.sortBy(remoteHost => remoteHost.distanceInKm)
    }

    def findClientInfo(hostingPlayer: PlayerId, hostEndpoint: IpEndpoint, clientSessionId: ClientSessionId): Option[PlayerSessionInfo] = {
      for {
        host <- db.hosts.get(hostEndpoint)
        if host.hostingPlayer.id == hostingPlayer
        client <- host.clients.get(clientSessionId)
      } yield PlayerSessionInfo(client.sessionId, client.secret, client.player.info)
    }
  }


}
