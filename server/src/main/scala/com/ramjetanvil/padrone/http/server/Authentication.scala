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
import java.util.Base64

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives._
import akka.http.scaladsl.server.directives.Credentials._
import akka.http.scaladsl.server.directives.FutureDirectives._
import akka.http.scaladsl.server.directives.HeaderDirectives._
import akka.http.scaladsl.server.directives.RouteDirectives._
import akka.http.scaladsl.server.directives.{AuthenticationDirective, AuthenticationResult, Credentials}
import com.google.api.client.util.Charsets
import com.ramjetanvil.padrone.AppConfig
import com.ramjetanvil.padrone.AppConfig
import com.ramjetanvil.padrone.http.client.Licensing.{LicenseVerifier, PlayerId}
import com.ramjetanvil.padrone.http.client.{Licensing, itch, oculus, steam}
import com.ramjetanvil.padrone.http.server.DataTypes.{Player, PlayerInfo}
import com.ramjetanvil.padrone.util.Util.FutureExtensions
import com.typesafe.scalalogging.Logger
import com.ramjetanvil.padrone.AppConfig
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object Authentication {
  type BasicHttpAuthenticator[T] = BasicHttpCredentials => Future[Option[T]]

  def authenticateBasicAsync[T](realm: String, authenticator: BasicHttpAuthenticator[T]): AuthenticationDirective[T] = {
    import akka.http.scaladsl.server.directives.SecurityDirectives._

    extractExecutionContext.flatMap { implicit ec =>
      authenticateOrRejectWithChallenge[BasicHttpCredentials, T] { credentials =>
        val user = credentials
          .map(authenticator)
          .getOrElse(Future(None))
        user.map {
          case Some(t) => AuthenticationResult.success(t)
          case None => AuthenticationResult.failWithChallenge(HttpChallenges.basic(realm))
        }
      }
    }
  }

  object UserAuthentication {
    case class AuthToken(method: String, credentials: String)
    type UserAuthenticator = AuthToken => Future[Player]

    case class AuthenticationException(message: String) extends Exception(message)

    def authenticatePlayer(authHeader: String)(authenticator: UserAuthenticator): AuthenticationDirective[Player] = {
      parseAuthHeader(authHeader)
        .map {
          case Some(authToken) => authenticator(authToken)
          case _ => Future.failed(AuthenticationException(s"No (valid) '$authHeader' header present"))
        }
        .flatMap(playerLogin => {
          onComplete(playerLogin).flatMap {
            case Success(user) => provide(user)
            case Failure(e) => reject(AuthorizationFailedRejection): Directive1[Player]
          }
        })
    }

    def parseAuthHeader(authHeader: String): Directive1[Option[AuthToken]] = {
      optionalHeaderValueByName(authHeader)
        .map { headerValue =>
          for {
            base64Value <- headerValue
            header <- Try {
              val header = new String(Base64.getDecoder.decode(base64Value), Charsets.UTF_8)
              header.split(":")
            }.toOption
            authenticationMethod <- header.headOption
            credentials <- header.lift(1)
          } yield {
            AuthToken(authenticationMethod, credentials)
          }
        }
    }

    def toCacheKey(authToken: AuthToken): String = s"${authToken.method}:${authToken.credentials}"

    type PartialAuthenticator = PartialFunction[AuthToken, Future[Player]]
    type AuthTokenConverter[LoginCredentials] = PartialFunction[AuthToken, Option[LoginCredentials]]

    implicit class PartialAuthenticatorExtensions(authenticator: PartialAuthenticator) {
      def cacheLogins[Serialization](implicit userCache: scalacache.TypedApi[Player, Serialization],
                                              ec: ExecutionContext): PartialAuthenticator = {
        case authToken if authenticator.isDefinedAt(authToken) =>
          userCache.caching(toCacheKey(authToken)) {
            authenticator(authToken)
          }
      }

      def build(implicit ec: ExecutionContext): UserAuthenticator = {
        authToken => {
          authenticator.lift(authToken)
            .getOrElse(Future.failed(AuthenticationException(s"Wrong authentication token supplied: $authToken")))
        }
      }
    }

    def logout[Serialization](token: AuthToken)
                             (implicit userCache: scalacache.TypedApi[Player, Serialization]): Unit = {
      userCache.remove(toCacheKey(token))
    }

    implicit class AuthTokenConverterExtensions[LoginCredentials](converter: AuthTokenConverter[LoginCredentials]) {
      def toUserAuthenticator()(implicit loginService: LicenseVerifier[LoginCredentials],
                                ec: ExecutionContext,
                                logger: Logger): PartialAuthenticator = {
        converter.andThen {
          case Some(credentials) => Licensing.verify(credentials)
          case None => Future.failed(new Exception(s"Incorrect auth token provided"))
        }
      }
    }

    object AuthTokenConverters {
      val Admin = AdminAuthentication.adminTokenConverter

      val ItchUserApiKey: AuthTokenConverter[itch.AuthToken] = {
        case AuthToken(method, credentials) if method == "itch.apikey" =>
          Some(itch.AuthToken(itch.UserApiKey(credentials)))
      }

      val ItchDownloadKey: AuthTokenConverter[itch.DownloadKey] = {
        case AuthToken(method, credentials) if method == "itch.downloadkey" =>
          Some(itch.DownloadKey(credentials))
      }

      val Oculus: AuthTokenConverter[oculus.AuthToken] = {
        case authToken if authToken.method == "oculus" =>
          for {
            credentials <- Try(authToken.credentials.split(",")).toOption
            userId <- credentials.headOption
            nonce <- credentials.lift(1)
          } yield oculus.AuthToken(oculus.OculusUserId(userId), oculus.Nonce(nonce))
      }

      val Steam: AuthTokenConverter[steam.AuthSessionTicket] = {
        case AuthToken(method, credentials) if method == "steam" =>
          Some(steam.AuthSessionTicket(credentials))
      }
    }

    object AdminAuthentication {

      import com.ramjetanvil.padrone.util.Util.StringExtensions

      case class AdminPlayerId(username: String) extends PlayerId {
        override def serialized: String = username
        override def toString: String = s"admin:$serialized"
      }

      case class AdminCredentials(username: String, password: String)

      val DefaultAdmin = adminPlayer("admin")
      def adminPlayer(username: String) = {
        val avatarUrl = s"""${AppConfig.Server.getString("url")}/app/resources/avatars/admin.jpg"""
        val info = PlayerInfo(
          Some(username.capitalize),
          Some(avatarUrl),
          isAdmin = true,
          isDeveloper = true)
        Player(AdminPlayerId(username), info)
      }
      val hashedAdminPassword = AppConfig.Server.getString("admin-password")

      val adminTokenConverter: AuthTokenConverter[AdminCredentials] = {
        case AuthToken(method, credentials) if method == "admin" =>
          val (username, password) = credentials.splitFirst(",")
          Some(AdminCredentials(username, password))
      }

      def adminLicenseVerifier()(implicit ec: ExecutionContext): LicenseVerifier[AdminCredentials] = new LicenseVerifier[AdminCredentials] {
        override def verify(credentials: AdminCredentials): Future[Player] = {
          if(BCrypt.checkpw(credentials.password, hashedAdminPassword)) {
            Future(adminPlayer(credentials.username))
          } else {
            Future.failed(new Exception("invalid admin password given"))
          }
        }
      }

      def basicHttpAdminAuthenticator(implicit ec: ExecutionContext): BasicHttpAuthenticator[Player] = { credentials =>
        if(BCrypt.checkpw(credentials.password, hashedAdminPassword)) {
          Future(Some(adminPlayer(credentials.username)))
        } else {
          Future(None)
        }
      }
    }
  }
}
