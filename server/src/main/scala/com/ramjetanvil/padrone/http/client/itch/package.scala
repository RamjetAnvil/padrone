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

import java.time.Instant

import akka.stream.Materializer
import com.ramjetanvil.padrone.http.client.HttpClient._
import com.ramjetanvil.padrone.http.client.Licensing.{LicenseVerifier, PlayerId}
import com.ramjetanvil.padrone.util.Util._
import itch.Client._

import scala.concurrent.{ExecutionContext, Future}

import com.ramjetanvil.padrone.http.client.itch.LicenseVerifiers._

package object itch {

  def extractDownloadKey(licenseUrl: String): DownloadKey = {
    //http://leafo.itch.io/x-moon/download/YWKse5jeAeuZ8w3a5qO2b2PId1sChw2B9b637w6z
    val key = licenseUrl
        .trim
        .trimRight("/")
        .split("/")
        .last
    DownloadKey(key)
  }

  def httpClient(httpClient: HttpClient, config: Configuration)
                (implicit fm: Materializer, ec: ExecutionContext): ItchIoClient =
    new ItchIoHttpClient(httpClient, config)
  def downloadKeyVerifier(itchIoClient: ItchIoClient, config: Configuration)
                         (implicit ec: ExecutionContext): LicenseVerifier[DownloadKey] =
    new DownloadKeyLicenseVerifier(itchIoClient, config.developers)
  def userApiKeyVerifier(itchIoClient: ItchIoClient, config: Configuration)
                        (implicit ec: ExecutionContext): LicenseVerifier[AuthToken] =
    new UserApiKeyLicenseVerifier(itchIoClient, config.developers)
  def config = Configuration.fromAppConfig _

  case class ItchIoDownloadKeyUserId(downloadKey: DownloadKey) extends PlayerId {
    override def serialized: String = downloadKey.value
    override def toString: String = s"itch.download-key:${downloadKey.value}"
  }

  case class ItchIoException(message: String) extends Exception(message)

  trait ItchIoClient {
    val gameId: GameId
    def fetchUserDetails(userApiKey: UserApiKey): Future[UserDetails]
    def fetchMyGames(): Future[Games]
    def fetchPurchases(userId: UserId): Future[Purchases]
    def fetchPurchasesByEmail(emailAddress: String): Future[Purchases]
    def fetchDownloadKeyInfo(downloadKey: DownloadKey): Future[DownloadKeyInfo]
    def fetchDownloadKeyInfoByUserId(userId: UserId): Future[DownloadKeyInfo]
    def fetchDownloadKeyInfoByEmail(emailAddress: String): Future[DownloadKeyInfo]
  }

  // Data types
  case class UserApiKey(value: String) extends AnyVal
  case class UserId(value: BigInt) extends PlayerId {
    override def serialized: String = value.toString
    override def toString: String = s"itch.user:$value"
  }
  object UserId {
    def fromString(value: String): UserId = UserId(BigInt(value))
  }
  case class User(displayName: Option[String], username: String, id: UserId,
                  url: String, coverUrl: Option[String],
                  gamer: Boolean, pressUser: Boolean, developer: Boolean)
  case class UserDetails(user: User)
  case class DownloadKey(value: String) extends AnyVal
  case class GameId(value: BigInt) extends AnyVal
  case class Games(games: Seq[Game])
  case class Game(coverUrl: String, createdAt: Instant, downloadsCount: BigInt, id: GameId,
                  minPrice: BigDecimal, pAndroid: Boolean, pLinux: Boolean, pOsx: Boolean,
                  pWindows: Boolean, published: Boolean, publishedAt: Instant,
                  purchasesCount: BigInt, shortText: String, title: String, gameType: String,
                  url: String, viewsCount: BigInt, earnings: Seq[Earning])
  case class Earning(currency: String, amountFormatted: String, amount: BigInt)
  case class Purchases(purchases: Seq[Purchase])
  case class Purchase(donation: Boolean, id: BigInt, email: String, createdAt: Instant, source: String,
                      currency: String, price: String, saleRate: BigInt,
                      gameId: GameId)
  case class DownloadKeyInfo(downloadKey: DownloadKeyStatus)
  case class DownloadKeyStatus(gameId: GameId, key: DownloadKey, id: BigInt, createdAt: Instant, downloads: BigInt,
                               owner: Option[User])
  case class Errors(errors: Seq[String])

  case class AuthToken(userApiKey: UserApiKey)
}
