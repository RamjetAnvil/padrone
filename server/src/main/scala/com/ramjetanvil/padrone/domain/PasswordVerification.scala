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

import org.mindrot.jbcrypt.BCrypt

object PasswordVerification {

  case class BCryptHash(hashedPassword: String) {
    def checkPassword(password: String) = BCrypt.checkpw(password, hashedPassword)
  }
  object BCryptHash {
    val random = new SecureRandom()
    def create(hashStrength: Int)(password: String): BCryptHash = {
      BCryptHash(BCrypt.hashpw(password, BCrypt.gensalt(hashStrength, random)))
    }
  }

  implicit class OptionalPassword(storedSecret: Option[BCryptHash]) {
    def verify(password: Option[String]): Boolean = {
      storedSecret match {
        case Some(hash) => password.exists(hash.checkPassword)
        case None => true
      }
    }
  }

}
