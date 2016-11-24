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

package com.ramjetanvil.padrone

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.ramjetanvil.padrone.http.client.HttpClient._
import com.ramjetanvil.padrone.http.server.HttpApi
import com.ramjetanvil.padrone.util.geo.GeoDb
import com.ramjetanvil.padrone.http.client._
import com.ramjetanvil.padrone.util.Quartz.QuartzScheduler
import com.ramjetanvil.padrone.util.geo.GeoDb
import com.ramjetanvil.padrone.util.geo.GeoDb._
import com.typesafe.scalalogging.Logger
import org.quartz.impl.StdSchedulerFactory
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends App {
  implicit val logger = Logger(LoggerFactory.getLogger("com.ramjetanvil.padrone"))
  implicit val system = ActorSystem("server-api")
  implicit val materializer = ActorMaterializer()
  implicit val scheduler = system.scheduler
  import system.dispatcher

  implicit val quartzScheduler = new QuartzScheduler(new StdSchedulerFactory().getScheduler)
  quartzScheduler.start()

  val geoDb = GeoDb.createDb()
  scheduleDbReload(geoDb)
  implicit val locationDb: LocationDb = GeoDb.toLocationDb(geoDb)

  val httpClient = HttpClient.createHttpClient()

  val port = AppConfig.Server.getInt("port")
  val bindingFuture = Http().bindAndHandle(
    HttpApi.create(httpClient),
    AppConfig.Server.getString("interface"),
    AppConfig.Server.getInt("port"))

  logger.info(s"Started web server at ${AppConfig.Server.getString("interface")}:${AppConfig.Server.getInt("port")}")

  Runtime.getRuntime.addShutdownHook(new Thread("Shutdown Akka HTTP") {
    override def run(): Unit = {
      logger.info("Shutting down...")
      Await.ready(bindingFuture.flatMap(_.unbind()), 5.minutes)
      quartzScheduler.close()
      system.terminate()
      materializer.shutdown()
    }
  })
}
