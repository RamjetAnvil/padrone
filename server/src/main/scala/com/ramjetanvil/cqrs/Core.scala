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

package com.ramjetanvil.cqrs

import akka.actor.{Actor, ActorSystem, Props}
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Core {

  /**
    * A command handler produces zero or more events from a given state and a command.
    */
  type CommandHandler[TCommand, TState, TEvent] = (TState, TCommand) => CommandHandlerResult[TEvent]
  type CommandHandlerResult[TEvent] = Try[Seq[TEvent]]

  type DispatchCommand[TCommand, TState, TEvent] = TCommand => Future[CommandHandlerResult[TEvent]]

  /**
    * The event handler applies an event to a given state to produce a new version of the state
    */
  type EventHandler[TState, TEvent] = (TState, TEvent) => TState

  object EventHandling {
    def applyEvents[TState, TEvent](eventHandler: EventHandler[TState, TEvent])
                                   (aggregateState: TState, events: Seq[TEvent]) = {
      events.foldLeft(aggregateState)(eventHandler)
    }

    def withoutMetadata[TMetadata, TEvent](events: Seq[(TMetadata, TEvent)]): Seq[TEvent] = {
      events.map(_._2)
    }
  }

  object CommandHandling {
    import EventHandling._

    def updateAggregate[TCommand, TState, TEvent]
                       (commandHandler: CommandHandler[TCommand, TState, TEvent])
                       (eventHandler: EventHandler[TState, TEvent])
                       (aggregateState: TState, command: TCommand): (TState, CommandHandlerResult[TEvent]) = {
      val commandResult = commandHandler(aggregateState, command)
      val updatedAggregateState = commandResult match {
        case Success(events) => applyEvents(eventHandler)(aggregateState, events)
        case Failure(_) => aggregateState
      }
      (updatedAggregateState, commandResult)
    }

    def withMetadata[TMetadata, TEvent](metadata: TMetadata)
                                       (commandResult: CommandHandlerResult[TEvent]): CommandHandlerResult[(TMetadata, TEvent)] = {
      commandResult.map(events => events.map((metadata, _)))
    }

    def extractEvents[TEvent](commandResult: CommandHandlerResult[TEvent]): Seq[TEvent] = {
      commandResult.toOption.toSeq.flatten
    }

    // Command handler return types
    def succeed[TEvent]: CommandHandlerResult[TEvent] = Success(Seq.empty)
    def succeedWith[TEvent](event: TEvent): CommandHandlerResult[TEvent] = Success(Seq(event))
    def succeedWith[TEvent](events: Seq[TEvent]): CommandHandlerResult[TEvent] = Success(events)

    def fail[TEvent]: CommandHandlerResult[TEvent] = Failure(new Exception())
    def failWith[TEvent](exception: Exception): CommandHandlerResult[TEvent] = Failure(exception)
  }

  trait AggregateRoot[TCommand, TState, TEvent] {
    val dispatchCommand: DispatchCommand[TCommand, TState, TEvent]
    def updates: Observable[(TState, Seq[TEvent])]
  }

  implicit class AggregateRootExtensions[TCommand, TState, TEvent](aggregateRoot: AggregateRoot[TCommand, TState, TEvent]) {
    import com.ramjetanvil.padrone.util.Util.ObservableExtensions

    def currentState: Future[TState] = aggregateRoot.state.toFuture
    def state: Observable[TState] = aggregateRoot.updates.map {
      case (state, _) => state
    }
    def events: Observable[TEvent] = aggregateRoot.updates.flatMap {
      case (_, events) => Observable.from(events)
    }
    def eventsWithoutMetadata[TMetadata, TEventWithoutMeta](implicit ev: TEvent <:< (TMetadata, TEventWithoutMeta)): Observable[TEventWithoutMeta] = {
      events.map(_._2)
    }
  }

  // TODO Actor needs to be cleaned up somehow
  class ActorAggregateRoot[TCommand, TState, TEvent](commandHandler: CommandHandler[TCommand, TState, TEvent])
                                                    (eventHandler: EventHandler[TState, TEvent])
                                                    (initialState: TState)
                                                    (implicit actorSystem: ActorSystem,
                                                     logger: Logger) extends AggregateRoot[TCommand, TState, TEvent] {
    private var _currentState = initialState
    private val _updates = BehaviorSubject((initialState, Seq.empty[TEvent]))
    private val updateAggregate = CommandHandling.updateAggregate(commandHandler)(eventHandler) _

    private val actor = actorSystem.actorOf(Props(new Actor {
      override def receive: Receive = {
        case obj =>
          val command = obj.asInstanceOf[TCommand]
          val (newState, result) = updateAggregate(_currentState, command)

          result match {
            case Success(newEvents) =>
              _currentState = newState
              _updates.onNext((newState, newEvents))
              logger.trace(s"Command handled:\n" +
                           s"$command ->\n${newEvents.mkString(" -> ")} ->\n$newState")
            case Failure(e) =>
              logger.info(s"(failure) Command($command) on $newState failed due to: '${e.getMessage}'")
          }

          sender() ! result
      }
    }))

    // TODO Find out how to use Akka streams to allow for back pressure on the dispatch
    override val dispatchCommand: DispatchCommand[TCommand, TState, TEvent] = command => {
      import akka.pattern.ask
      import scala.concurrent.duration._

      implicit val timeout = Timeout(60.seconds)
      (actor ? command).mapTo[CommandHandlerResult[TEvent]]
    }

    override val updates: Observable[(TState, Seq[TEvent])] = _updates
  }

}
