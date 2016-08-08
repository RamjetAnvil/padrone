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

package com.ramjetanvil.cqrs.experimental

import com.ramjetanvil.cqrs.Core.{CommandHandler, CommandHandlerResult, EventHandler}

import scala.util.Success

object PartialHandlers {

  type PartialCommandHandler[TCommand, TState, TEvent] = PartialFunction[(TState, TCommand), CommandHandlerResult[TEvent]]
  type PartialEventHandler[TState, TEvent] = PartialFunction[(TState, TEvent), TState]

  // TODO Factories don't work because of erasure
//  class CommandHandlerFactory[TCommand, TState, TEvent] {
//    def handler[T <: TCommand](handler: CommandHandler[T, TState, TEvent]): PartialCommandHandler[TCommand, TState, TEvent] = {
//      case (state, command: T) => handler(state, command)
//    }
//  }

  def combineCommandHandlers[TCommand, TState, TEvent](handlers: PartialCommandHandler[TCommand, TState, TEvent]*): CommandHandler[TCommand, TState, TEvent] = {
    val liftedHandler = handlers.reduce((h, singleHandler) => {
      h.orElse(singleHandler)
    }).lift
    val emptyResult = Success(Seq.empty[TEvent])
    // TODO Warn whenever an unhandled command is being processed
    (state, command) => liftedHandler(state, command).getOrElse(emptyResult)
  }

  // TODO Factories don't work because of erasure
//  class EventHandlerFactory[TState, TEvent] {
//    def handler[T <: TEvent](handler: EventHandler[TState, T]): PartialEventHandler[TState, TEvent] = {
//      case (state, event: T) => handler(state, event)
//    }
//  }

  def combineEventHandlers[TState, TEvent](handlers: PartialEventHandler[TState, TEvent]*): EventHandler[TState, TEvent] = {
    val liftedHandler = handlers.reduce((h, singleHandler) => {
      h.orElse(singleHandler)
    }).lift
    // TODO Warn whenever an unhandled event is being processed
    (state, event) => liftedHandler(state, event).getOrElse(state)
  }

}
