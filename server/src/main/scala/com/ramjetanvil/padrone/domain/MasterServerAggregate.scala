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

import java.security.SecureRandom
import java.time.Instant

import akka.actor.ActorSystem
import com.ramjetanvil.padrone.util.IpEndpoint
import com.ramjetanvil.padrone.util.scheduling.ActorRescheduler
import com.ramjetanvil.cqrs.Core._
import com.ramjetanvil.padrone.domain.MasterServerQueryLayer.{ClientSecret, ClientSessionId, ClientState, Host, MasterServerDb}
import com.ramjetanvil.padrone.domain.PasswordVerification.BCryptHash
import com.ramjetanvil.padrone.http.client.Licensing.PlayerId
import com.ramjetanvil.padrone.http.server.Authentication.UserAuthentication.AdminAuthentication.AdminPlayerId
import com.ramjetanvil.padrone.http.server.DataTypes.GameCommunicationProtocol.HostRegistrationRequest
import com.ramjetanvil.padrone.http.server.DataTypes.Player
import com.ramjetanvil.padrone.util.IpEndpoint
import com.ramjetanvil.padrone.util.geo.GeoDb.{Location, LocationDb}
import com.typesafe.scalalogging.Logger
import monocle.macros.GenLens
import com.ramjetanvil.padrone.util.IpEndpoint
import org.mindrot.jbcrypt.BCrypt
import rx.lang.scala.{Observable, Subscription}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

object MasterServerAggregate {

  case class Metadata(player: Player, timestamp: Instant)

  sealed trait Command
  object Command {
    final case class RegisterHost(request: HostRegistrationRequest) extends Command
    final case class UnregisterHost(externalEndpoint: IpEndpoint) extends Command
    final case class Ping(externalEndpoint: IpEndpoint, connectedClients: Set[ClientSessionId]) extends Command
    final case class PingTimeout(externalEndpoint: IpEndpoint) extends Command

    /* Joining a game server through the MS can only be done by a client, if the join succeeds a random id is generated
       and sent back to the client and it is stored in the master server db on the server
       the client wants to join.

       When the client then decides to join the game server it will also send the random id
       to the game server. This allows the game server to refer to this user when sending requests
       to the MS (for example for requesting the user's display name and avatar) and the game server can also authenticate
       that indeed this player had requested this join on the MS and has indeed a valid Volo license.

       Furthermore, this way we do not leak real Itch/Steam etc. user ids to the servers nor any other players. */
    final case class Join(hostEndpoint: IpEndpoint, password: Option[String], sessionId: ClientSessionId,
                          secret: ClientSecret) extends Command
    final case class ReportLeave(sessionId: ClientSessionId, hostEndpoint: IpEndpoint) extends Command
    case object Leave extends Command
  }
  type CommandWithMeta = (Metadata, Command)

  sealed trait Event
  object Event {
    final case class HostRegistered(host: Host) extends Event
    final case class HostUnregistered(externalEndpoint: IpEndpoint) extends Event
    final case class Pinged(externalEndpoint: IpEndpoint) extends Event

    final case class Joined(client: Player, hostEndpoint: IpEndpoint, sessionId: ClientSessionId,
                            secret: ClientSecret) extends Event
    final case class Left(userId: PlayerId, hostEndpoint: IpEndpoint) extends Event
  }
  type EventWithMeta = (Metadata, Event)

  def createAggregateRoot(pingTimeout: FiniteDuration, joinTimeout: FiniteDuration, bcryptHashStrength: Int)
                         (implicit locationDb: LocationDb,
                          ec: ExecutionContext,
                          actorSystem: ActorSystem,
                          logger: Logger): AggregateRoot[CommandWithMeta, MasterServerDb, EventWithMeta] = {

    import monocle.function.all.at
    import monocle.std.map._
    import monocle.std.option.some
    import monocle.{Lens, Optional}

    val hashPassword = BCryptHash.create(bcryptHashStrength) _

    val registeredHosts = GenLens[MasterServerDb](_.hosts)
    def registeredHostAt(endpoint: IpEndpoint): Lens[MasterServerDb, Option[Host]] = {
      registeredHosts.composeLens(at(endpoint))
    }
    val clientsLens = GenLens[Host](_.clients)
    def clientList(hostEndpoint: IpEndpoint): Optional[MasterServerDb, Map[ClientSessionId, ClientState]] = {
      //https://groups.google.com/forum/#!topic/scala-monocle/i7Y4o0I7tIc
      val hostLens = registeredHostAt(hostEndpoint) composePrism some
      hostLens composeLens clientsLens
    }
    val clientLens = GenLens[MasterServerDb](_.clients)
    def clientAt(playerId: PlayerId): Lens[MasterServerDb, Option[ClientState]] = {
      clientLens.composeLens(at(playerId))
    }

    val commandHandler: CommandHandler[CommandWithMeta, MasterServerDb, EventWithMeta] = { case (db, (meta, command)) =>
      import com.ramjetanvil.padrone.util.Util.Time._
      import CommandHandling._
      import Command._
      import Event._

      val player = meta.player

      def findHost(endpoint: IpEndpoint) = registeredHostAt(endpoint).get(db)

      def isAllowedToModify(host: Host) = host.hostingPlayer.id == player.id || player.isAdmin

      def unregisterHost(host: Host): Seq[Event] = {
        val leaveEvents = host.clients
          .map { case (_, clientState) => Left(clientState.player.id, host.externalEndpoint) }
          .toSeq
        leaveEvents :+ HostUnregistered(host.externalEndpoint)
      }

      val commandResult: Try[Seq[Event]] = command match {
        case RegisterHost(HostRegistrationRequest(hostName, peerInfo, password, shouldAdvertise, version, maxPlayers)) =>
          val newHost = Host(
            peerInfo,
            hostName,
            password.map(hashPassword),
            shouldAdvertise,
            peerInfo.location,
            player,
            version,
            registeredAt = meta.timestamp,
            lastPingReceived = meta.timestamp,
            maxPlayers = maxPlayers)

          findHost(peerInfo.externalEndpoint) match {
            case Some(existingHost) =>
              if(isAllowedToModify(existingHost)) {
                succeedWith(unregisterHost(existingHost) :+ HostRegistered(newHost))
              } else {
                failWith(new Exception(s" $player is unauthorized to re-register at endpoint ${peerInfo.externalEndpoint}"))
              }
            case None => succeedWith(HostRegistered(newHost))
          }

        case UnregisterHost(externalEndpoint) =>
          findHost(externalEndpoint).filter(isAllowedToModify) match {
            case Some(host) => succeedWith(unregisterHost(host))
            case None => failWith(new Exception(s"Cannot unregister host $externalEndpoint because it isn't registered" +
                " or the user is not authorized to unregister this host"))
          }

        case Ping(externalEndpoint, connectedClients) =>
          findHost(externalEndpoint).filter(isAllowedToModify) match {
            case Some(host) =>
              val unconnectedClients = (host.clients -- connectedClients).values.toSeq
              val leaveEvents = unconnectedClients.flatMap { client =>
                val isJoinTimedOut = client.joinTimestamp + joinTimeout < meta.timestamp
                if(isJoinTimedOut) {
                  Some(Left(client.player.id, externalEndpoint))
                } else {
                  None
                }
              }

              succeedWith(leaveEvents :+ Pinged(externalEndpoint))
            case None => failWith(new Exception(s"Failed to ping $externalEndpoint because it isn't registered" +
                " or the user is not authorized to ping this host"))
          }

        case PingTimeout(externalEndpoint) =>
          findHost(externalEndpoint).filter(isAllowedToModify) match {
            case Some(host) if Instant.now > (host.lastPingReceived + pingTimeout) =>
              succeedWith(unregisterHost(host))
            case None =>
              // Nothing left to do the host is no longer registered
              succeed
          }

        case Join(hostEndpoint, password, sessionId, secret) =>
          findHost(hostEndpoint) match {
            case Some(host) if !host.isFull && host.password.verify(password) =>
              val maybeLeaveEvent = clientAt(player.id).get(db).map { leaver =>
                Left(leaver.player.id, leaver.joinedIp)
              }.toSeq
              val joinEvent = Joined(player, host.endpoint.externalEndpoint, sessionId, secret)
              succeedWith(maybeLeaveEvent :+ joinEvent)
            case None => fail
          }

        case Leave =>
          clientAt(player.id).get(db) match {
            case Some(leaver) => succeedWith(Left(leaver.player.id, leaver.joinedIp))
            case None => succeed // Apparently the client already left
          }

        case ReportLeave(sessionId, hostEndpoint) =>
          val maybeLeaver = for {
            host <- findHost(hostEndpoint)
            if isAllowedToModify(host)
            leaver <- host.clients.get(sessionId)
          } yield leaver
          maybeLeaver match {
            case Some(leaver) => succeedWith(Left(leaver.player.id, hostEndpoint))
            case None => succeed // Apparently the client already left
          }
      }

      withMetadata(meta)(commandResult)
    }

    val eventHandler: EventHandler[MasterServerDb, EventWithMeta] = { case (db, (Metadata(_, timestamp), event)) =>
      import Event._

      // TODO Use the state monad to allow for incremental state updates
      val updateDb = event match {
        case HostRegistered(host) =>
          registeredHosts.modify(hosts => hosts + (host.endpoint.externalEndpoint -> host))

        case HostUnregistered(externalEndpoint) =>
          registeredHosts.modify(hosts => hosts - externalEndpoint)

        case Pinged(externalEndpoint) =>
          (registeredHostAt(externalEndpoint) composePrism some).modify { host =>
            host.copy(lastPingReceived = timestamp)
          }

        case Joined(player, hostEndpoint, sessionId, secret) =>
          db: MasterServerDb => {
            val clientState = ClientState(player, hostEndpoint, sessionId, secret, timestamp)
            val newDb = clientList(hostEndpoint).modify { clients =>
              clients + (sessionId -> clientState)
            }(db)
            clientAt(player.id).set(Some(clientState))(newDb)
          }

        case Left(playerId, hostEndpoint) =>
          db: MasterServerDb => {
            val newDb = clientList(hostEndpoint).modify { clients =>
              clientAt(playerId).get(db) match {
                case Some(clientState) => clients - clientState.sessionId
                case None => clients
              }
            }(db)
            clientLens.modify(clients => clients - playerId)(newDb)
          }
      }
      updateDb(db)
    }

    new ActorAggregateRoot(commandHandler)(eventHandler)(MasterServerDb.initialValue)
  }

  def pingScheduler(pingTimeout: FiniteDuration,
                    dispatchCommand: Command => Unit,
                    events: Observable[Event])
                   (implicit actorSystem: ActorSystem): Subscription = {
    import scala.concurrent.duration._
    import Command._
    import Event._

    val pingScheduler = new ActorRescheduler[IpEndpoint]

    def scheduleRegistrationTimeout(endpoint: IpEndpoint): Unit = {
      pingScheduler.schedule(endpoint, pingTimeout, 10.seconds) {
        dispatchCommand(PingTimeout(endpoint))
      }
    }

    // TODO Make sure it gets handled on a single thread
    val sub = events.subscribe(event => {
      event match {
        case HostRegistered(host) =>
          scheduleRegistrationTimeout(host.externalEndpoint)
        case HostUnregistered(endpoint) =>
          pingScheduler.cancelSchedule(endpoint)
        case Pinged(endpoint) =>
          scheduleRegistrationTimeout(endpoint)
        case _ => // Ignore the rest
      }
    })
    Subscription {
      sub.unsubscribe()
      pingScheduler.close()
    }
  }
}