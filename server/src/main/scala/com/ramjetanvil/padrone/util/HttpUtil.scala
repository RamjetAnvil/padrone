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

package com.ramjetanvil.padrone.util

import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.Sink
import rx.lang.scala.Observable
import spray.json.JsonWriter

object HttpUtil {

  import akka.http.scaladsl.server.Directives._
  import com.ramjetanvil.padrone.util.Util.ObservableExtensions
  import JsonUtil._

  implicit class WebSocketUpgradeHeader(header: UpgradeToWebSocket) {
    def handleWith[T](stream: Observable[T])(implicit jsonWriter: JsonWriter[T]) = {
      complete {
        val wsStream = stream
          .toSource
          .map(_.toJson.toTextMessage)
        header.handleMessagesWithSinkSource(Sink.ignore, wsStream)
      }
    }
  }

  def handleWebSocketWith[T](stream: Observable[T])(implicit jsonWriter: JsonWriter[T]) = {
    extractUpgradeToWebSocket(upgrade => upgrade.handleWith(stream))
  }

}
