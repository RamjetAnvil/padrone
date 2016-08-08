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

import akka.stream.Materializer
import com.ramjetanvil.padrone.http.client.HttpClient.HttpClient
import com.ramjetanvil.padrone.http.client.Licensing.{LicenseException, LicenseVerifier, PlayerId}
import com.ramjetanvil.padrone.http.client.oculus.Client.{Configuration, OculusHttpClient}

import scala.concurrent.{ExecutionContext, Future}

package object oculus {

  def httpClient(httpClient: HttpClient, config: Configuration)
                (implicit fm: Materializer, ec: ExecutionContext): OculusClient = new OculusHttpClient(httpClient, config)
  def licenseVerifier(client: OculusClient)(implicit ec: ExecutionContext): LicenseVerifier[AuthToken] = new OculusLicenseVerifier(client)
  def config = Configuration.fromAppConfig _

  case class OculusUserId(value: String) extends PlayerId {
    override def serialized: String = value
    override def toString = s"oculus:$value"
  }
  case class UserAccessToken(value: String) extends AnyVal
  case class AppId(value: String) extends AnyVal
  case class AppSecret(value: String) extends AnyVal

  case class Nonce(value: String) extends AnyVal
  case class AuthToken(userId: OculusUserId, nonce: Nonce)

  trait OculusClient {
    def fetchUserId(accessToken: UserAccessToken): Future[OculusUserId]
    def authenticateUser(userId: OculusUserId, nonce: Nonce): Future[OculusUserId]
  }
}
