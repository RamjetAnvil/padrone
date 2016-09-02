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

import java.net.InetAddress
import java.time.Instant

import com.ramjetanvil.padrone.domain.MasterServerAggregate.Event.HostRegistered
import com.ramjetanvil.padrone.domain.MasterServerAggregate.Metadata
import com.ramjetanvil.padrone.domain.MasterServerQueryLayer.{GameVersion, Host, HostName, MasterServerDb, PeerInfo}
import com.ramjetanvil.padrone.http.client.Licensing.PlayerId
import com.ramjetanvil.padrone.http.server.DataTypes.Player
import com.ramjetanvil.padrone.util.IpEndpoint
import com.ramjetanvil.padrone.util.geo.GeoDb.LocationDb
import org.scalatest.FunSuite

import scala.util.Failure

/**
  * Created by frank on 2-9-2016.
  */
class MasterServerDbViewTest extends FunSuite {
  import MasterServerQueryLayer._

  val testPlayer = Player(new PlayerId {
    override def serialized: String = "test-player"
  })
  val gameVersion = GameVersion("test")
  implicit val mockLocationDb: LocationDb = address => Failure(new Exception("No location found"))

  test("testListHosts") {
    val host = Host(
      endpoint = PeerInfo(IpEndpoint(InetAddress.getLocalHost, 20000), IpEndpoint(InetAddress.getLocalHost, 20000)),
      name = HostName("test"),
      password = None,
      isPrivate = false,
      location = None,
      hostingPlayer = testPlayer,
      version = gameVersion,
      registeredAt = Instant.now,
      lastPingReceived = Instant.now,
      maxPlayers = 4)
    val db = MasterServerAggregate.handleEvent(
      MasterServerDb.initialValue,
      (Metadata(testPlayer, Instant.now), HostRegistered(host)))

    val listedHosts = db.listHosts(gameVersion, hideFull = true, hidePasswordProtected = true, peerAddress = None)
    assert(listedHosts.length == 1)
  }

}
