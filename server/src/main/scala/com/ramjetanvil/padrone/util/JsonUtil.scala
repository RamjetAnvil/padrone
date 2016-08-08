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

import akka.http.scaladsl.model.ws.TextMessage
import spray.json._

object JsonUtil {

  def singleValueFormat[TWrapper, TValue](construct: TValue => TWrapper, deconstruct: TWrapper => TValue)
                                         (implicit valueFormat: JsonFormat[TValue]) = new JsonFormat[TWrapper] {
    override def write(obj: TWrapper): JsValue = valueFormat.write(deconstruct(obj))
    override def read(json: JsValue): TWrapper = construct(valueFormat.read(json))
  }

  def singleValueRootFormat[TWrapper, TValue](construct: TValue => TWrapper, get: TWrapper => TValue)
                                             (implicit valueFormat: RootJsonFormat[TValue]) = {
    DefaultJsonProtocol.rootFormat(singleValueFormat(construct, get))
  }

  implicit class ToJsonConverter[T](value: T)(implicit writer: JsonWriter[T]) {
    def toJson: JsValue = writer.write(value)
  }

  implicit class JsValueHttpExtensions(value: JsValue) {
    def toTextMessage: TextMessage = TextMessage(value.compactPrint)
  }


}
