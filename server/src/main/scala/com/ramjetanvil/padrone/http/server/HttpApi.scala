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

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentType, ContentTypes, StatusCodes}
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileAndResourceDirectives
import akka.stream.Materializer
import com.ramjetanvil.padrone.AppConfig
import com.ramjetanvil.padrone.domain.MasterServerAggregate
import com.ramjetanvil.padrone.domain.MasterServerAggregate.Command.Leave
import com.ramjetanvil.padrone.http.client.HttpClient
import com.ramjetanvil.padrone.http.server.Authentication.UserAuthentication.{AdminAuthentication, AuthTokenConverters}
import com.ramjetanvil.padrone.http.server.DataTypes.GameCommunicationProtocol.HostRegistrationRequest
import com.ramjetanvil.padrone.AppConfig
import com.ramjetanvil.padrone.domain.MasterServerAggregate
import com.ramjetanvil.padrone.domain.MasterServerAggregate.Command._
import com.ramjetanvil.padrone.domain.MasterServerAggregate.Metadata
import com.ramjetanvil.padrone.domain.MasterServerQueryLayer._
import com.ramjetanvil.padrone.http.JsonProtocols
import com.ramjetanvil.padrone.http.client._
import com.ramjetanvil.padrone.http.server.Authentication.UserAuthentication._
import com.ramjetanvil.padrone.http.server.DataTypes.GameCommunicationProtocol.HostRegistrationRequest
import com.ramjetanvil.padrone.util.IpEndpoint
import com.ramjetanvil.padrone.util.geo.GeoDb.LocationDb
import com.typesafe.scalalogging.Logger
import com.ramjetanvil.padrone.AppConfig
import com.ramjetanvil.padrone.domain.MasterServerAggregate
import com.ramjetanvil.padrone.domain.MasterServerAggregate.Command.Leave
import com.ramjetanvil.padrone.http.client.HttpClient
import com.ramjetanvil.padrone.http.server.Authentication.UserAuthentication.{AdminAuthentication, AuthTokenConverters}
import com.ramjetanvil.padrone.http.server.DataTypes.GameCommunicationProtocol.HostRegistrationRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// Parameterize over domain?

object HttpApi {

  import DataTypes._
  import com.ramjetanvil.padrone.http.JsonProtocols.GameCommunication._
  import com.ramjetanvil.padrone.http.JsonProtocols._
  import akka.http.scaladsl.server.Directives._

  def create()(implicit actorSystem: ActorSystem, ec: ExecutionContext, materializer: Materializer,
               locationDb: LocationDb, logger: Logger) = {
    import Authentication._
    import GameCommunicationProtocol._
    import com.ramjetanvil.cqrs.Core.AggregateRootExtensions
    import com.ramjetanvil.padrone.domain.MasterServerQueryLayer.MasterServerDbView
    import com.ramjetanvil.padrone.util.HttpUtil._
    import com.ramjetanvil.padrone.util.Util.DurationConversion._
    import com.ramjetanvil.padrone.util.Util.NumericExtensions
    import com.ramjetanvil.padrone.http.Marshallers._

    val pingTimeout = AppConfig.Server.getDuration("ping-timeout").toScalaDuration.asInstanceOf[FiniteDuration]
    val joinTimeout = AppConfig.Server.getDuration("join-timeout").toScalaDuration.asInstanceOf[FiniteDuration]
    val authHeader = AppConfig.Server.getString("auth-header")
    val masterServer = MasterServerAggregate.createAggregateRoot(pingTimeout, joinTimeout)
    val dispatchCommandAsAdmin: MasterServerAggregate.Command => Unit = command => {
      val metadata = Metadata(AdminAuthentication.DefaultAdmin, timestamp = Instant.now)
      masterServer.dispatchCommand((metadata, command))
    }
    val cancelPingScheduler = MasterServerAggregate.pingScheduler(
      pingTimeout,
      dispatchCommandAsAdmin,
      masterServer.eventsWithoutMetadata)

    implicit val loggedInUserCache = {
      import com.github.benmanes.caffeine.cache.Caffeine

      import scalacache._
      import caffeine._

      val cacheTimeout = AppConfig.Server.getDuration("login-cache-timeout")
      val maxCachedUsers = AppConfig.Server.getInt("max-cached-logins")
      implicit val cache = ScalaCache(CaffeineCache(Caffeine.newBuilder()
        .expireAfterWrite(cacheTimeout.getSeconds, TimeUnit.SECONDS)
        .maximumSize(maxCachedUsers)
        .build[String, Object]))
      typed[Player, NoSerialization]
    }
    val authenticatePlayer = {
      val httpClient = HttpClient.createHttpClient()

      val itchConfig = itch.config(AppConfig.Itch)
      val itchIoClient = itch.httpClient(httpClient, itchConfig)
      implicit val itchDownloadKeyVerifier = itch.downloadKeyVerifier(itchIoClient, itchConfig)
      implicit val itchUserApiVerifier = itch.userApiKeyVerifier(itchIoClient, itchConfig)

      implicit val steamLoginService = {
        val config = steam.config(AppConfig.Steam)
        val client = steam.httpClient(httpClient, config)
        steam.licenseVerifier(client, config)
      }

      implicit val oculusLoginService = {
        val client = oculus.httpClient(httpClient, oculus.config(AppConfig.Oculus))
        oculus.licenseVerifier(client)
      }

      implicit val adminLoginService = AdminAuthentication.adminLicenseVerifier()

      val cachedAuthenticators = AuthTokenConverters.Steam.toUserAuthenticator()
        .orElse(AuthTokenConverters.Oculus.toUserAuthenticator())
        .orElse(AuthTokenConverters.ItchUserApiKey.toUserAuthenticator())
        .orElse(AuthTokenConverters.ItchDownloadKey.toUserAuthenticator())
        .cacheLogins
      val nonCachedAuthenticators = AuthTokenConverters.Admin.toUserAuthenticator()
      val authenticators = cachedAuthenticators
        .orElse(nonCachedAuthenticators)
        .build
      UserAuthentication.authenticatePlayer(authHeader)(authenticators)
    }

    val adminRoute =
      authenticateBasicAsync("admin", AdminAuthentication.basicHttpAdminAuthenticator) { adminUser =>
        pathPrefix("app-state") {
          (pathEnd & extractRequest) { request =>
            val appState = masterServer.state
              .sample(1.seconds)
              .map(toRenderableAppState)
            request.header[UpgradeToWebSocket] match {
              case Some(upgrade) => upgrade.handleWith(appState.onBackpressureLatest)
              case None => encodeResponse(complete(StatusCodes.OK, appState))
            }
          } ~
          path("live-view") {
            encodeResponse {
              FileAndResourceDirectives.getFromResource("web/admin/app-state.html")
            }
          } ~
          pathPrefix("resources") {
            encodeResponse {
              FileAndResourceDirectives.getFromResourceDirectory("web/default")
            }
          }
        }
      }

    // TODO Hosting is not limited to a Player to allow for dedicated servers to be hosted
    // TODO A Player may only host one game at a time

    val appRoute =
      get {
        path("health-check") {
          complete(StatusCodes.OK)
        } ~
        pathPrefix("resources") {
          pathPrefix("avatars") {
            FileAndResourceDirectives.getFromResourceDirectory("web/avatars")
          }
        } ~
        authenticatePlayer { player =>
          path("me") {
            complete(player.info)
          } ~
          (path("list-hosts") & parameters('version.as[String],
                                           'hideFull.as[Boolean] ? true,
                                           'limit.as[Int] ? 50)) { (version, hideFull, limit) =>
            extractClientIP { clientAddress =>
              encodeResponse {
                complete {
                  val hostLimit = limit.clamp(0, 100)
                  val hosts = masterServer.currentState.map { state =>
                    state.listHosts(GameVersion(version), hideFull, clientAddress.toOption)
                      .take(hostLimit)
                  }
                  hosts
                }
              }
            }
          } ~
          (path("player-info") & parameters('sessionId.as[String],
                                            'hostEndpoint.as[String])) { (sessionIdStr, hostEndpointStr) =>
            val host = player
            val sessionId = ClientSessionId(sessionIdStr)
            val hostEndpoint = IpEndpoint.parse(hostEndpointStr).toOption
            complete {
              masterServer.currentState
                .map { db =>
                  val maybeClientInfo = for {
                    hostEndpoint <- hostEndpoint
                    clientInfo <- db.findClientInfo(host.id, hostEndpoint, sessionId)
                  } yield clientInfo
                  maybeClientInfo match {
                    case Some(info) => (StatusCodes.OK, Some(info))
                    case None => (StatusCodes.NotFound, None)
                  }
                }
            }
          }
        }
      } ~
      post {
        authenticatePlayer { player =>
          import MasterServerAggregate.{Command, Metadata}

          def dispatchCommand(command: Command) = {
            val metadata = Metadata(player, timestamp = Instant.now)
            masterServer.dispatchCommand((metadata, command))
          }

          path("register-host") {
            entity(as[HostRegistrationRequest]) { registrationRequest =>
              complete {
                // TODO Limit one host per player
                val registration = dispatchCommand(RegisterHost(santizeRegistration(registrationRequest)))
                registration.map {
                  case Success(_) ⇒ StatusCodes.OK
                  case Failure(e) ⇒ StatusCodes.NotAcceptable
                }
              }
            }
          } ~
          path("unregister-host") {
            entity(as[IpEndpoint]) { externalIpEndpoint =>
              complete {
                dispatchCommand(UnregisterHost(externalIpEndpoint))
                StatusCodes.OK
              }
            }
          } ~
          (path("ping") & entity(as[PingRequest])) { pingRequest =>
            complete {
              dispatchCommand(Ping(pingRequest.hostEndpoint, pingRequest.connectedClients)).map {
                case Success(_) => StatusCodes.OK
                case Failure(_) => StatusCodes.NotAcceptable
              }
            }
          } ~
          (path("join") & entity(as[JoinRequest])) { joinRequest =>
            complete {
              val sessionId = ClientSessionId.generate()
              val secret = ClientSecret.generate()
              dispatchCommand(Join(joinRequest.hostEndpoint, sessionId, secret)).map {
                case Success(_) => (StatusCodes.OK, Some(JoinResponse(sessionId, secret)))
                case Failure(_) => (StatusCodes.NotAcceptable, None)
              }
            }
          } ~
          path("leave") {
            complete {
              dispatchCommand(Leave).map {
                case Success(_) => StatusCodes.OK
                case Failure(_) => StatusCodes.NotAcceptable
              }
            }
          } ~
          (path("report-leave") & entity(as[ReportLeaveRequest])) { leaveReport =>
            complete {
              dispatchCommand(ReportLeave(leaveReport.sessionId, leaveReport.hostEndpoint)).map {
                case Success(_) => StatusCodes.OK
                case Failure(_) => StatusCodes.NotAcceptable
              }
            }
          }
        }
      }

    Route.seal {
      pathPrefix("admin") {
        adminRoute
      } ~
      pathPrefix("app") {
        appRoute
      }
    }
  }

  def santizeRegistration(registration: HostRegistrationRequest): HostRegistrationRequest = {
    import com.ramjetanvil.padrone.util.Util.StringExtensions
    registration.copy(
      hostName = HostName(registration.hostName.value.limit(100, "...")))
  }
}
