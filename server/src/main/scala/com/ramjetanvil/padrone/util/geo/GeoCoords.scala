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

import com.ramjetanvil.padrone.util.UnitOfMeasure.Meters
import com.ramjetanvil.padrone.util.UnitOfMeasure.Meters
import com.ramjetanvil.padrone.util.UnitOfMeasure.Meters

case class GeoCoords(latitude: Double, longitude: Double)

object GeoCoords {

  final val EarthRadius: Meters = Meters(6371000)

  implicit class GeoCoordsExtensions(coords: GeoCoords) {
    /**
     * Compares two geo coordinates using the haversine method.
     *
     * @param other the geo coordinates to compare to
     * @return the distance in meters to the other geo coordinates
     */
    // TODO This algorithm is quite heavy, maybe we can optimize?
    def distanceTo(other: GeoCoords): Meters = {
      val deltaLat = Math.toRadians(other.latitude - coords.latitude)
      val deltaLon = Math.toRadians(other.longitude - coords.longitude)
      val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
        Math.cos(Math.toRadians(coords.latitude)) *
        Math.cos(Math.toRadians(other.latitude)) *
        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
      val c = Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 2
      val d = EarthRadius.value * c
      Meters(d)
    }

    /**
     * Allows geo coordinates to be ordered by distance to these coordinates.
     *
     * @return an Ordering type to be used with .sorted()/.sortBy()
     */
    def distanceOrdering: Ordering[GeoCoords] = {
      Ordering[Double].on[GeoCoords](_.distanceTo(coords).value)
    }
  }

  /**
   * Allows geo coordinates to be sorted by which one is most central to the given geoCoords.
   *
   * @param geoCoords the coordinates to use to check which one is most centralized
   * @return an Ordering type to be used with .sorted()/.sortBy()
   */
  def centralizedOrdering(geoCoords: Seq[GeoCoords]): Ordering[GeoCoords] = {
    require(geoCoords.nonEmpty, "Coords may not be empty")

    def sortByFurthest(reference: GeoCoords) = geoCoords.sorted(reference.distanceOrdering.reverse)
    Ordering[Double].on[GeoCoords](location => {
      val furthest = sortByFurthest(location).head
      location.distanceTo(furthest).value
    })
  }
}
