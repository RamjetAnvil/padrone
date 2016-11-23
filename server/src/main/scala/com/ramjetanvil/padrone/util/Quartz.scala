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

import java.time.Instant
import java.util.UUID

import com.typesafe.scalalogging.Logger
import org.quartz._
import rx.lang.scala.{Observer, Subscription, Observable}

import scala.concurrent.ExecutionContext

object Quartz {

  class QuartzScheduler(val scheduler: Scheduler)(implicit ec: ExecutionContext) extends AutoCloseable {
    private val cancellable = new AsyncCancellable

    def start() = scheduler.start()
    override def close(): Unit = {
      cancellable.close()
      scheduler.shutdown(true)
    }
    val onClose = cancellable.onCancel
    val whenClosed = cancellable.whenClosed _
  }

  private class CronJob extends Job {
    override def execute(context: JobExecutionContext): Unit = {
      val jobId = context.getMergedJobDataMap.get("jobId").asInstanceOf[String]
      val obs = context.getScheduler.getContext.get(jobId).asInstanceOf[Observer[Instant]]
      obs.onNext(Instant.now())
    }
  }

  implicit class SchedulerExtensions(quartzScheduler: QuartzScheduler) {

    /**
      * Creates an Observable that triggers based on a Cron expression
      *
      * @param cronExpression a valid cron expression
      * @see http://www.quartz-scheduler.org/documentation/quartz-1.x/tutorials/crontrigger
      * @return
      */
    def createCronJob(cronExpression: String)(implicit logger: Logger): Observable[Instant] = {
      import JobBuilder._
      import TriggerBuilder._
      import CronScheduleBuilder._

      Observable(obs => {
        val jobId = UUID.randomUUID().toString

        val job = newJob(classOf[CronJob])
          .usingJobData("jobId", jobId)
          .build()
        val trigger = newTrigger()
          .withSchedule(cronSchedule(cronExpression))
          .build()

        val scheduler = quartzScheduler.scheduler
        scheduler.getContext.put(jobId, obs)
        scheduler.scheduleJob(job, trigger)

        val subscription = Subscription {
          obs.onCompleted()
          try {
            scheduler.deleteJob(job.getKey)
            scheduler.getContext.remove(jobId)
          } catch {
            case e: Exception =>
              logger.debug(s"Something went wrong when deleting quartz jobs, due to: ${e.getMessage}")
          }
        }

        obs.add(subscription)
        quartzScheduler.whenClosed(() => subscription.unsubscribe())
      })
    }

  }
}

