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

package com.ramjetanvil.padrone.util.geo

import java.io.{File, FileOutputStream}
import java.net.{InetAddress, URL}
import java.nio.channels.Channels
import java.nio.file.StandardCopyOption._
import java.nio.file.{Files, Paths}
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

import akka.actor.Scheduler
import com.maxmind.db.Reader.FileMode
import com.maxmind.geoip2.{DatabaseReader, GeoIp2Provider}
import com.ramjetanvil.padrone.util.Quartz.QuartzScheduler
import com.ramjetanvil.padrone.util.AsyncCancellable
import com.ramjetanvil.padrone.util.Quartz.{QuartzScheduler, SchedulerExtensions}
import com.ramjetanvil.padrone.util.Util.FuturesUtil._
import com.ramjetanvil.padrone.util.Util.{FutureExtensions, ObservableExtensions}
import com.typesafe.scalalogging.Logger
import com.ramjetanvil.padrone.util.Quartz.QuartzScheduler

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.stm.Ref
import scala.util.{Failure, Success, Try}

object GeoDb {

  case class Country(isoCode: String) extends AnyVal {
    def name: String = new Locale("", isoCode).getDisplayCountry
  }
  case class Location(coordinates: GeoCoords, country: Country)

  type LocationDb = InetAddress => Try[Location]

  val RemoteDatabaseUrl = new URL("http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.mmdb.gz")
  val DatabasePath = "GeoLite2-City.mmdb"

  class MaxmindGeoDb(dbReader: DatabaseReader) extends AutoCloseable {
    val db: LocationDb = inetAddress => {
      Try(dbReader.city(inetAddress)).map(city => {
        Location(
          GeoCoords(city.getLocation.getLatitude, city.getLocation.getLongitude),
          Country(city.getCountry.getIsoCode))
      })
    }
    override def close(): Unit = dbReader.close()
  }

  private def loadDb(databasePath: String): MaxmindGeoDb = {
    val dbReader = new DatabaseReader.Builder(new File(databasePath))
      .fileMode(FileMode.MEMORY)
      .build()
    new MaxmindGeoDb(dbReader)
  }

  /**
   * Loads the database located at ./GeoLite2-City.mmdb
   *
   * @return the database loaded in memory
   */
  def createDb(databasePath: String = DatabasePath)
              (implicit logger: Logger): Ref[MaxmindGeoDb] = {
    if(!new File(databasePath).exists()) {
      downloadDb(RemoteDatabaseUrl, databasePath)
    }
    Ref(loadDb(DatabasePath))
  }

  def reloadDb(db: Ref[MaxmindGeoDb])(databasePath: String = DatabasePath)
              (implicit logger: Logger): Unit = {
    val oldDb = db.single.swap(loadDb(DatabasePath))
    oldDb.close()
    logger.info("Reloaded GeoDB")
  }

  def downloadDb(url: URL, downloadPath: String = DatabasePath)
                (implicit logger: Logger): Unit = {
    import resource._

    logger.info("Downloading GeoDB...")

    val tempDownloadPath = downloadPath + ".temp"
    for {
      rbc <- managed(Channels.newChannel(new GZIPInputStream(url.openStream())))
      tempFileStream <- managed(new FileOutputStream(tempDownloadPath))
    } {
      tempFileStream.getChannel.transferFrom(rbc, 0, Long.MaxValue)
    }
    Files.move(Paths.get(tempDownloadPath), Paths.get(downloadPath), REPLACE_EXISTING)

    logger.info("...finished downloading GeoDB")
  }

  def scheduleDbReload(dbRef: Ref[MaxmindGeoDb], downloadPath: String = DatabasePath,
                       maxRetries: Int = 5, retryTimeOut: FiniteDuration = 2.hours)
                      (implicit ec: ExecutionContext,
                       quartzScheduler: QuartzScheduler,
                       akkaScheduler: Scheduler,
                       logger: Logger): Unit = {
    async {
      while(true) {
        // 10:15am every first Thursday of the month
        await(quartzScheduler.createCronJob("0 15 10 ? * 5#1").toFuture)
        // Every minute
        //await(scheduler.createCronJob("0/10 * * * * ?").toFuture)

        var currentTry = 0
        var isFinished = false
        while(currentTry < maxRetries && !isFinished) {
          await(awaitable(retryTimeOut * currentTry))

          Try(downloadDb(RemoteDatabaseUrl, downloadPath)) match {
            case Success(_) =>
              reloadDb(dbRef)(downloadPath)
              isFinished = true
            case Failure(e) =>
              logger.warn(s"Failed to download GeoDb due to: $e")
              currentTry = currentTry + 1
          }
        }
      }
    }
  }

  def toLocationDb(geoDbRef: Ref[MaxmindGeoDb]): LocationDb = {
    inetAddress => geoDbRef.single.get.db(inetAddress)
  }
}
