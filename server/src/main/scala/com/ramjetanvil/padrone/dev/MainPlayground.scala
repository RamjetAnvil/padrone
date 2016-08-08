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

package com.ramjetanvil.padrone.dev

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object MainPlayground extends App {
//  implicit val system = ActorSystem("server-api")
//  implicit val materializer = ActorMaterializer()
//  implicit val scheduler = system.scheduler

//  val oculusClient = oculus.httpClient(HttpClient.createHttpClient(), oculus.config(AppConfig.Oculus)).asInstanceOf[OculusHttpClient]
//  // 1013516932049277 = fversnel
//  oculusClient.authenticateUser(oculus.UserId("1013516932049277"), "h23OtmpjntSs1rIczq4ovFdWd57xWARXxLyXGtSSBk15vthjfacZRHFs6VIQIQVZ")
//    .subscribe(Console.println, Console.println)

  //'2007-12-03T10:15:30.00Z'
  println(Instant.parse("2016-07-20T17:56:04Z"))
}
