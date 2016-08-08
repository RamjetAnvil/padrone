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

package com.ramjetanvil.padrone.util.scheduling

import akka.actor.{Actor, ActorSystem, Cancellable, Props}

import scala.concurrent.duration.FiniteDuration

class ActorRescheduler[Key](implicit actorSystem: ActorSystem) extends Rescheduler[Key] with AutoCloseable {

  sealed trait ScheduleAction
  case class ScheduleOnce(key: Key, delay: FiniteDuration, work: () => Unit) extends ScheduleAction
  case class Schedule(key: Key, initialDelay: FiniteDuration, interval: FiniteDuration, work: () => Unit)
  case class RemoveSchedule(key: Key) extends ScheduleAction

  val scheduler = actorSystem.actorOf(Props(new Actor {
    import scala.collection.mutable
    import actorSystem.dispatcher

    val schedules = mutable.Map[Key, Cancellable]()

    override def receive = {
      case ScheduleOnce(key, delay, work) =>
        storeSchedule(key, actorSystem.scheduler.scheduleOnce(delay)(work()))
      case Schedule(key, initialDelay, interval, work) =>
        storeSchedule(key, actorSystem.scheduler.schedule(initialDelay, interval)(work()))
      case RemoveSchedule(key) => removeSchedule(key)
    }

    private def storeSchedule(key: Key, cancellable: Cancellable) = {
      removeSchedule(key)
      schedules += (key -> cancellable)
    }

    private def removeSchedule(key: Key) = schedules.remove(key).foreach(_.cancel())
  }))

  override def scheduleOnce(key: Key, delay: FiniteDuration)(work: => Unit): Unit = {
    scheduler ! ScheduleOnce(key, delay, () => work)
  }

  override def schedule(key: Key, initialDelay: FiniteDuration, interval: FiniteDuration)(work: => Unit): Unit = {
    scheduler ! Schedule(key, initialDelay, interval, () => work)
  }

  override def cancelSchedule(key: Key): Unit = {
    scheduler ! RemoveSchedule(key)
  }

  override def close(): Unit = {
    actorSystem.stop(scheduler)
  }
}
