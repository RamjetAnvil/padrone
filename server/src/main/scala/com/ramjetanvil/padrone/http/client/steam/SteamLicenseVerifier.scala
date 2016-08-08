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

package com.ramjetanvil.padrone.http.client.steam

import com.ramjetanvil.padrone.http.client.Licensing.{LicenseException, LicenseVerifier, PlayerId}
import com.ramjetanvil.padrone.http.server.DataTypes.{Player, PlayerInfo}
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}

class SteamLicenseVerifier(steamClient: SteamClient, developers: Set[SteamUserId])
                          (implicit logger: Logger, ec: ExecutionContext) extends LicenseVerifier[AuthSessionTicket] {
  override def verify(authSessionTicket: AuthSessionTicket): Future[Player] = {
    for {
      userId <- steamClient.authenticateUser(authSessionTicket)
      (license, userDetails) <- steamClient.fetchGameLicense(userId).zip(steamClient.fetchUserDetails(userId))
    } yield {
      val info = PlayerInfo(
        Some(userDetails.name),
        userDetails.avatarUrl,
        isDeveloper = developers.contains(userId))
      Player(license.userId, info)
    }
  }
}