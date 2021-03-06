/* Copyright (C) 2012-2013 Treode, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.treode.cps.stub.io

import java.net.SocketAddress
import java.nio.channels._
import scala.collection.mutable
import com.treode.cps.Thunk
import com.treode.cps.io.{ServerSocket, Socket}
import com.treode.cps.scheduler.Scheduler

class ServerSocketStub (protected implicit val scheduler: Scheduler)
extends AbstractSocketStub with ServerSocket {
  import scheduler.{spawn, suspend}

  initialize (Unbound)

  private def acceptPending = new AcceptPendingException
  private def alreadyBound = new AlreadyBoundException
  private def notYetBound = new NotYetBoundException

  protected trait State extends AbstractSocketState {

    def accept (k: Thunk [Socket]): Option [Unit]

    def bind (local: SocketAddress, backlog: Int = 0): Option [Unit]

    def accepted (): Option [Unit] =
      throw new IllegalStateException ("Cannot have accepted when not accepting.")
  }

  private object Unbound extends AbstractUnconnectedState with State {

    def close () = move (this, ClosedByClose) (())

    def accept (k: Thunk [Socket]) = effect (k.fail (notYetBound))

    def bind (local: SocketAddress, backlog: Int) =
      local match {
        case local: SocketAddressStub =>
          move (this, new Bound (local)) (())
        case _ =>
          throw unsupportedAddressType
      }

    def shutdown () = move (this, ClosedByShutdown) (())
  }

  private class Bound (local: SocketAddressStub) extends State {

    def close () = move (this, ClosedByClose) (())

    def localAddress = effect (Some (local))

    def accept (k: Thunk [Socket]) =
      move (this, new Accepting (local, k)) {
        spawn {
          val s = local.accept ()
          ServerSocketStub.this.accepted ()
          k (s)
        }}

    def bind (local: SocketAddress, backlog: Int) = throw alreadyBound

    def shutdown () = move (this, ClosedByShutdown) (())
  }

  private class Accepting (local: SocketAddressStub, k: Thunk [Socket]) extends State {

    def close () = move (this, ClosedByClose) (k.fail (asyncClose))

    def localAddress = effect (Some (local))

    def accept (k: Thunk [Socket]) = effect (k.fail (acceptPending))

    def bind (local: SocketAddress, backlog: Int) = throw alreadyBound

    def shutdown () = move (this, ClosedByShutdown) (k.fail (asyncClose))

    override def accepted () = move (this, new Bound (local)) (())
  }

  private trait AbstractClosed extends State {

    def accept (k: Thunk [Socket]) = effect (k.fail (closed))

    def bind (local: SocketAddress, backlog: Int) = throw closed
  }

  private object ClosedByClose extends AbstractClosed with AbstractClosedByClose

  private object ClosedByShutdown extends AbstractClosed with AbstractClosedByShutdown

  private [io] def accepted () = delegate (_.accepted ())

  def accept () = suspend [Socket] (k => delegate (_.accept (k)))

  def bind (local: SocketAddress, backlog: Int = 0): this.type = {
    delegate (_.bind (local, backlog))
    this
  }}

object ServerSocketStub {

  /** Create a new stub server socket which passes data in process and does not involve any OS
    * networking.
    *
    * @param s The scheduler for continuing execution when data is received.  These stub sockets
    *     are not multithread safe, so the scheduler must be single threaded, such as with
    *     CpsSpecKit.Sequential or RandomKit.
    */
  def apply () (implicit s: Scheduler): ServerSocket = new ServerSocketStub ()
}
