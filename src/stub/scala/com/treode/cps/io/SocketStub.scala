/* Copyright (C) 2012 Treode, Inc.
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

package com.treode.cps
package io

import java.net.{SocketAddress, SocketOption}
import java.nio.ByteBuffer
import java.nio.channels._
import scala.util.Random
import com.treode.cps.scheduler.Scheduler
import com.treode.cps.sync.Mailbox

class SocketAddressStub (random: Random, scheduler: Scheduler) extends SocketAddress {

  private [this] val mb = Mailbox [SocketStub] (scheduler)

  private [io] def accept () = {
    val otherSocket = mb.receive ()
    val otherAddress = new SocketAddressStub (random, scheduler)
    val s1 = new Simplex (random, scheduler)
    val s2 = new Simplex (random, scheduler)
    otherSocket.connected (otherAddress, this, s1, s2)
    val thisSocket = new SocketStub (scheduler)
    thisSocket.connected (this, otherAddress, s2, s1)
    thisSocket
  }

  private [io] def connect (s: SocketStub) = mb.send (s)

  override def toString =
    "SocketAddressStub (0x" + System.identityHashCode (this).toHexString + ")"
}

object SocketAddressStub {

  /** Create a new stub socket address which links a client to a server in process and does not
    * invole any OS networking.
    *
    * @param s The scheduler for continuing execution when data is received.  These stub sockets
    *     are not multithread safe, so the scheduler must be single threaded, such as with
    *     CpsSpecKit.Sequential or RandomKit.
    */
  def apply (r: Random, s: Scheduler): SocketAddress = new SocketAddressStub (r, s)
}

class SocketStub (protected val scheduler: Scheduler) extends AbstractSocketStub with Socket {

  initialize (Unconnected)

  private [this] def alreadyConnected = new AlreadyConnectedException
  private [this] def connectionPending = new ConnectionPendingException
  private [this] def notYetConnected = new NotYetConnectedException

  protected [this] trait State extends AbstractSocketState {
    def connect (remote: SocketAddress) (k: Thunk [Unit]): Behavior [Unit]
    def remoteAddress: Behavior [Option [SocketAddress]]
    def read (dst: ByteBuffer): SuspendableBehavior [Int]
    def write (src: ByteBuffer): SuspendableBehavior [Int]
    def connected (local: SocketAddress, remote: SocketAddress, output: Simplex, input: Simplex): Behavior [Unit] =
      illegalState ("Cannot complete connect when not unconnected or connecting.")
  }

  protected [this] object Unconnected extends AbstractUnconnectedState with State {
    def close () = moveTo (ClosedByClose) withoutEffect
    def connect (remote: SocketAddress) (k: Thunk [Unit]) =
      remote match {
        case remote: SocketAddressStub =>
          moveTo (new Connecting (k)) withEffect (remote.connect (SocketStub.this))
        case _ =>
          effect (k.fail (unsupportedAddressType))
      }
    def remoteAddress = effect (None)
    def read (dst: ByteBuffer) = tossS (notYetConnected)
    def write (src: ByteBuffer) = tossS (notYetConnected)
    override def connected (local: SocketAddress, remote: SocketAddress, output: Simplex, input: Simplex) =
      moveTo (new Connected (local, remote, output, input)) withoutEffect
    def shutdown () = moveTo (ClosedByShutdown) withoutEffect
  }

  protected [this] class Connecting (k: Thunk [Unit]) extends AbstractUnconnectedState with State {
    def close () = moveTo (ClosedByClose) withEffect (k.fail (asyncClose))
    def connect (remote: SocketAddress) (k: Thunk [Unit]) = effect (k.fail (connectionPending))
    def remoteAddress = effect (None)
    def read (dst: ByteBuffer) = tossS (notYetConnected)
    def write (src: ByteBuffer) = tossS (notYetConnected)
    override def connected (local: SocketAddress, remote: SocketAddress, output: Simplex, input: Simplex) =
      moveTo (new Connected (local, remote, output, input)) withEffect (k ())
    def shutdown () = moveTo (ClosedByShutdown) withEffect (k.fail (asyncClose))
  }

  protected [this] class Connected (local: SocketAddress, remote: SocketAddress, output: Simplex, input: Simplex)
  extends State {
    def close () = moveTo (ClosedByClose) withoutEffect
    def localAddress = effect (Some (local))
    def connect (remote: SocketAddress) (k: Thunk [Unit]) = effect (k.fail (alreadyConnected))
    def remoteAddress = effect (Some (remote))
    def read (dst: ByteBuffer) = effectS (input.read (dst))
    def write (src: ByteBuffer) = effectS (output.write (src))
    def shutdown () = moveTo (ClosedByShutdown) withoutEffect
  }

  private [this] trait AbstractClosed extends State {
    def connect (remote: SocketAddress) (k: Thunk [Unit]) = effect (k.fail (closed))
    def remoteAddress = toss (closed)
    def read (dst: ByteBuffer) = tossS (closed)
    def write (src: ByteBuffer) = tossS (closed)
  }
  private [this] object ClosedByClose extends AbstractClosed with AbstractClosedByClose
  private [this] object ClosedByShutdown extends AbstractClosed with AbstractClosedByShutdown

  private [io] def connected (local: SocketAddress, remote: SocketAddress, output: Simplex, input: Simplex) =
    delegate (_.connected (local, remote, output, input))

  def connect (remote: SocketAddress) = delegateT (_.connect (remote))
  def remoteAddress = delegate (_.remoteAddress)
  def read (dst: ByteBuffer) = delegateS (_.read (dst))
  def write (src: ByteBuffer) = delegateS (_.write (src))

  def read (dst: Array [ByteBuffer]): Long @thunk = {
    // CPS frustrates more idiomatic expressions
    var len = 0L
    var i = 0
    var r = 0
    var n = 0
    while (i < dst.length && r <= n) {
      r = dst (i).remaining
      n = read (dst (i))
      len += n
      i += 1
    }
    if (n < 0) -1L else len
  }

  def write (src: Array [ByteBuffer]): Long @thunk = {
    var len = 0L
    var i = 0
    var r = 0
    var n = 0
    while (i < src.length && r <= n) {
      r = src (i).remaining
      n = write (src (i))
      len += n
      i += 1
    }
    if (n < 0) -1L else len
  }}

object SocketStub {

  /** Create a new stub socket which passes data in process and does not involve any OS networking.
    *
    * @param s The scheduler for continuing execution when data is received.  These stub sockets
    *     are not multithread safe, so the scheduler must be single threaded, such as with
    *     CpsSpecKit.Sequential or RandomKit.
    */
  def apply (s: Scheduler): Socket = new SocketStub (s: Scheduler)
}
