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

package com.ramjetanvil.padrone.http.client

import com.ramjetanvil.padrone.http.client.HttpClient.HttpClient
import com.ramjetanvil.padrone.http.client.Licensing.{LicenseVerifier, PlayerId}
import com.ramjetanvil.padrone.http.client.steam.Client.{Configuration, SteamHttpClient}
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}

package object steam {

  def httpClient(httpClient: HttpClient, config: Configuration)(implicit ec: ExecutionContext) =
    new SteamHttpClient(httpClient, config)
  def licenseVerifier(client: SteamClient, config: Configuration)
                     (implicit logger: Logger, ec: ExecutionContext): LicenseVerifier[AuthSessionTicket] =
    new SteamLicenseVerifier(client, config.developers)
  def config = Configuration.fromAppConfig _

  trait SteamClient {
    def fetchGameLicense(steamId: SteamUserId): Future[GameLicense]
    def fetchUserDetails(steamUserId: SteamUserId): Future[SteamUserDetails]
    def authenticateUser(ticket: AuthSessionTicket): Future[SteamUserId]
  }

  case class AuthSessionTicket(value: String) extends AnyVal
  case class SteamUserId(value: String) extends PlayerId {
    override def serialized: String = value
    override def toString = s"steam:$value"
  }
  case class GameLicense(userId: SteamUserId, isPermanent: Boolean)
  case class SteamUserDetails(id: SteamUserId, name: String, avatarUrl: Option[String] = None)
}
