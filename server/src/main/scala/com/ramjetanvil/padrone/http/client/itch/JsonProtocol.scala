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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.ramjetanvil.padrone.util.JsonUtil._
import com.scalapenos.spray.SnakifiedSprayJsonSupport
import spray.json._
import scala.util.{Failure, Success, Try}
import java.time.{ZoneOffset, LocalDateTime, Instant}
import java.time.format.DateTimeFormatter
import com.ramjetanvil.padrone.util.JsonUtil

object JsonProtocol extends SprayJsonSupport with SnakifiedSprayJsonSupport {
  implicit object JsonDateTimeFormat extends JsonFormat[Instant] {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss")
    override def write(dateTime: Instant): JsValue =  {
      JsString(dateTimeFormatter.format(dateTime))
    }

    override def read(json: JsValue): Instant = {
      json match {
        case JsString(dateTimeStr) =>
          Try(LocalDateTime.parse(dateTimeStr, dateTimeFormatter)) match {
            case Success(dateTime) => dateTime.toInstant(ZoneOffset.UTC)
            case Failure(e) => deserializationError(s"Failed to parse $dateTimeStr to DateTime object", e)
          }
        case jsValue =>
          deserializationError(s"Cannot parse $jsValue as LocalDateTime")
      }
    }
  }
  implicit val JsonUserIdFormat = singleValueFormat[UserId, BigInt](UserId.apply, _.value)
  implicit val JsonUserFormat = jsonFormat8(User)
  implicit val JsonUserDetailsFormat = jsonFormat1(UserDetails)
  implicit val JsonDownloadKeyFormat = singleValueFormat[DownloadKey, String](DownloadKey.apply, _.value)
  implicit val JsonGameIdFormat = singleValueFormat[GameId, BigInt](GameId.apply, _.value)
  implicit val JsonEarningProtocol = jsonFormat3(Earning)
  implicit val JsonGameProtocol = jsonFormat(Game.apply, "cover_url", "created_at", "downloads_count", "id",
    "min_price", "p_android", "p_linux", "p_osx", "p_windows", "published", "published_at", "purchases_count",
    "short_text", "title", "type", "url", "views_count", "earnings")
  implicit val JsonGamesProtocol = jsonFormat1(Games)
  implicit val JsonPurchaseProtocol = jsonFormat9(Purchase)
  implicit val JsonPurchasesProtocol = jsonFormat1(Purchases)
  implicit val JsonDownloadKeyStatusProtocol = jsonFormat6(DownloadKeyStatus)
  implicit val JsonDownloadKeyInfoProtocol = jsonFormat1(DownloadKeyInfo)
  implicit val JsonErrorsProtocol = jsonFormat1(Errors)

  implicit def JsonDataWithErrors[DataFormat](implicit dataFormat: RootJsonFormat[DataFormat]) = new RootJsonFormat[Either[Errors, DataFormat]] {
    override def write(obj: Either[Errors, DataFormat]): JsValue = {
      obj match {
        case Right(data) => dataFormat.write(data)
        case Left(errors) => JsonErrorsProtocol.write(errors)
      }
    }

    override def read(jsValue: JsValue): Either[Errors, DataFormat] = {
      val root = jsValue.asJsObject
      if(root.fields.contains("errors")) {
        Left(JsonErrorsProtocol.read(jsValue))
      } else {
        Right(dataFormat.read(jsValue))
      }
    }
  }
}

