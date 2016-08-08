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

import com.ramjetanvil.padrone.http.server.DataTypes.Player
import com.ramjetanvil.padrone.http.server.DataTypes.Player
import com.typesafe.scalalogging.Logger
import com.ramjetanvil.padrone.http.server.DataTypes.Player
import org.slf4j.Marker

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Licensing {

  trait PlayerId {
    def serialized: String
  }
  case class LicenseException(message: String, throwable: Throwable = null) extends Exception(message, throwable)

  trait LicenseVerifier[TCredentials] {

    /**
      * Verifies whether the user has a valid license to the game in question.
      *
      * @param credentials the credentials to check
      * @return a license if there is any, otherwise throws a LicenseException. Also provides any additional
      *         info about the user that was retrieved during the license check
      */
    def verify(credentials: TCredentials): Future[Player]
  }

  def verify[TCredentials](credentials: TCredentials)
                          (implicit verifier: LicenseVerifier[TCredentials],
                                    ec: ExecutionContext,
                                    logger: Logger): Future[Player] = {
    verifier.verify(credentials)
      .andThen {
        case Failure(ex) => logger.error(s"Verification failed for credentials $credentials", ex)
        case Success(player) => logger.info(s"Successfully logged in $player")
      }
  }
}