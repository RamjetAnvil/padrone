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

package com.ramjetanvil.padrone.http.client.itch

import com.ramjetanvil.padrone.http.client.Licensing.{LicenseException, LicenseVerifier}
import com.ramjetanvil.padrone.http.server.DataTypes.{Player, PlayerInfo}

import scala.concurrent.{ExecutionContext, Future}

object LicenseVerifiers {

  class UserApiKeyLicenseVerifier(client: ItchIoClient, developers: Set[UserId])
                                 (implicit ec: ExecutionContext) extends LicenseVerifier[AuthToken] {
    val gameId = client.gameId
    val userToPlayer = convertToPlayer(developers) _

    override def verify(authToken: AuthToken): Future[Player] = {
      val player = for {
        UserDetails(user) <- client.fetchUserDetails(authToken.userApiKey)
        _ <- client.fetchDownloadKeyInfoByUserId(user.id)
      } yield userToPlayer(user)
      player.transform(identity, convertItchExceptions)
    }
  }

  class DownloadKeyLicenseVerifier(client: ItchIoClient, developers: Set[UserId])
                                  (implicit ec: ExecutionContext) extends LicenseVerifier[DownloadKey] {
    val gameId = client.gameId
    val ownerToPlayer = convertToPlayer(developers) _

    override def verify(downloadKey: DownloadKey): Future[Player] = {
      val player = for {
        DownloadKeyInfo(keyInfo) <- client.fetchDownloadKeyInfo(downloadKey)
      } yield {
        keyInfo.owner match {
          case Some(owner) => ownerToPlayer(owner)
          case None => Player(ItchIoDownloadKeyUserId(downloadKey))
        }
      }
      player.transform(identity, convertItchExceptions)
    }
  }

  private def convertToPlayer(developers: Set[UserId])(user: User): Player = {
    val info = PlayerInfo(
      user.displayName,
      user.coverUrl,
      isDeveloper = developers.contains(user.id))
    Player(user.id, info)
  }

  private val convertItchExceptions: Throwable => Throwable = {
    case e: ItchIoException => LicenseException("Itch.io returned API call errors", e)
    case t => t
  }
}
