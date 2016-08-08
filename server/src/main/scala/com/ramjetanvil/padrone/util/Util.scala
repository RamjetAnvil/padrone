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
import java.time.temporal.Temporal
import java.util.concurrent.TimeoutException

import akka.NotUsed
import akka.actor.Scheduler
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.Source
import monocle.{Lens, PLens}
import org.reactivestreams.{Subscriber, Publisher}
import rx.RxReactiveStreams

import scala.annotation.tailrec
import scala.language.implicitConversions

import java.io.InputStream
import java.net.InetAddress
import java.security.{SecureRandom, KeyStore}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import com.google.common.net.HostAndPort
import rx.lang.scala.Observable

import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

case class IpEndpoint(address: InetAddress, port: Int) {
  override def toString: String = {
    HostAndPort.fromParts(address.getHostAddress, port).toString
  }
}
object IpEndpoint {
  def parse(hostAndPortExpression: String): Try[IpEndpoint] = {
    Try {
      val hostAndPort = HostAndPort.fromString(hostAndPortExpression)
      IpEndpoint(
        InetAddress.getByName(hostAndPort.getHostText),
        hostAndPort.getPort)
    }
  }
}


object Util {

  implicit class NumericExtensions[T](value: T)(implicit numeric: Numeric[T]) {
    def clamp(min: T, max: T): T = {
      numeric.max(min, numeric.min(value, max))
    }
  }

  implicit class StringExtensions(str: String) {
    def limit(charCount: Int, trailingChars: String = ""): String = {
      if(str.length > charCount) {
        str.substring(0, charCount) + trailingChars
      } else {
        str
      }
    }

    def trimLeft(charSequence: String): String = {
      @tailrec
      def inner(str: String): String = {
        if (str.startsWith(charSequence)) {
          inner(str.substring(charSequence.length, str.length))
        } else {
          str
        }
      }
      inner(str)
    }

    def trimRight(charSequence: String): String = {
      str.reverse.trimLeft(charSequence.reverse).reverse
    }

    def trim(charSequence: String): String = {
      str.trimLeft(charSequence)
        .trimRight(charSequence)
    }

    def splitFirst(charSequence: String): (String, String) = {
      val (first, second) = str.splitAt(str.indexOf(charSequence))
      (first, second.substring(1, second.length))
    }
  }

  object FuturesUtil {
    def after[T](construct: => Try[T])(duration: FiniteDuration)
                (implicit scheduler: Scheduler,
                          executionContext: ExecutionContext): Future[T] = {
      akka.pattern.after(duration, scheduler)(Future.fromTry(construct))
    }

    def timeout(duration: FiniteDuration)(implicit scheduler: Scheduler,
                                                   executionContext: ExecutionContext): Future[Nothing] = {
      after(Failure(new TimeoutException))(duration)
    }

    def awaitable(duration: FiniteDuration)(implicit scheduler: Scheduler,
                                                     executionContext: ExecutionContext): Future[Unit] = {
      after(Success(()))(duration)
    }
  }


  implicit class FutureExtensions[A](future: Future[A])(implicit ec: ExecutionContext) {
    def |(other: Future[A]): Future[A] = {
      Future.firstCompletedOf[A](Set(future, other))
    }
    def materialize: Future[Try[A]] = {
      val p = Promise[Try[A]]()
      future.onComplete(p.success)
      p.future
    }
    def dematerialize[B](implicit ev: A <:< Try[B]): Future[B] = {
      val p = Promise[B]
      future.onComplete {
        case Success(value) => p.complete(value)
        case Failure(cause) => p.failure(cause)
      }
      p.future
    }
    def toObservable: Observable[A] = Observable.from(future)
  }

  implicit class ObservableExtensions[A](observable: Observable[A]) {
    import rx.lang.scala.JavaConversions
    import akka.stream.scaladsl.Source
    import FastFuture._

    def toFuture: Future[A] = observable.take(1).toBlocking.toFuture
    def toFastFuture: FastFuture[A] = observable.toFuture.fast

    def toJavaObservable: rx.Observable[A] = JavaConversions.toJavaObservable(observable).asInstanceOf[rx.Observable[A]]

    // Reactive streams API
    def toPublisher: Publisher[A] = RxReactiveStreams.toPublisher(toJavaObservable)
    def toSource: Source[A, NotUsed] = Source.fromPublisher(toPublisher)
  }

  def reschedulable(scheduleDelays: Observable[Duration]): Observable[Unit] = {
    scheduleDelays.switchMap(Observable.timer).map(_ ⇒ Unit)
  }

  object DurationConversion {
    import java.util.concurrent.TimeUnit
    import java.time.{Duration ⇒ JavaDuration}
    import scala.concurrent.duration.{Duration ⇒ ScalaDuration}

    implicit def javaToScalaDuration(javaDuration: JavaDuration): ScalaDuration = {
      ScalaDuration(javaDuration.getSeconds, TimeUnit.SECONDS) +
        ScalaDuration(javaDuration.getNano, TimeUnit.NANOSECONDS)
    }

    implicit def scalaToJavaDuration(scalaDuration: ScalaDuration): JavaDuration = {
      JavaDuration.ofNanos(scalaDuration.toNanos)
    }

    implicit class JavaDurationExtensions(duration: JavaDuration) {
      def toScalaDuration = javaToScalaDuration(duration)
    }

    implicit class ScalaDurationExtensions(duration: ScalaDuration) {
      def toJavaDuration = scalaToJavaDuration(duration)
    }
  }

  object Time {
    implicit val instantOrdering: Ordering[Instant] = Ordering.ordered
    implicit val instantOrderingOps = instantOrdering.mkOrderingOps _

    implicit class TemporalExtensions[T <: Temporal](time: T) {
      import DurationConversion._

      def +(duration: Duration): T = {
        time.plus(duration.toJavaDuration).asInstanceOf[T]
      }
      def -(duration: Duration): T = {
        time.minus(duration.toJavaDuration).asInstanceOf[T]
      }
    }
  }
}
