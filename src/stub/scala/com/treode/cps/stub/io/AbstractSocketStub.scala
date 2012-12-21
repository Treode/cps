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

package com.treode.cps.stub.io

import java.net.{SocketAddress, SocketOption}
import java.nio.channels.{
  AsynchronousCloseException,
  ClosedChannelException,
  UnsupportedAddressTypeException}
import com.treode.cps.io.NetworkChannel
import com.treode.cps.scheduler.Scheduler
import com.treode.cps.sync.AtomicState

trait AbstractSocketStub extends NetworkChannel with AtomicState {

  protected def asyncClose = new AsynchronousCloseException
  protected def closed = new ClosedChannelException
  protected def unsupported = new UnsupportedOperationException
  protected def unsupportedAddressType = new UnsupportedAddressTypeException

  def getOption [A] (name: SocketOption [A]): A = throw unsupported
  def setOption [A] (name: SocketOption [A], value: A): this.type = throw unsupported
  def supportedOptions = Set [SocketOption [_]] ()

  protected trait AbstractSocketState {
    def close (): Option [Unit]
    def shutdown (): Option [Unit]
    def localAddress: Option [Option [SocketAddress]]
    def isOpen: Option [Boolean] = effect (true)
    def closedByShutdown: Option [Boolean] =
      throw new AssertionError ("Socket is still open.")
  }

  protected type State <: AbstractSocketState

  private [io] def shutdown () = delegate2 (_.shutdown ())

  def close () = delegate2 (_.close ())
  def isOpen = delegate2 (_.isOpen)
  def localAddress = delegate2 (_.localAddress)

  /** The stack trace recording this socket's creation. */
  val createdAt = Thread.currentThread.getStackTrace

  /** Was this socket closed by `close` or system shutdown? */
  def closedByShutdown = delegate2 (_.closedByShutdown)

  protected trait AbstractUnconnectedState extends AbstractSocketState {
    def localAddress = effect (None)
  }

  protected trait AbstractClosedByClose extends AbstractSocketState {
    def close () = effect ()
    def shutdown () = effect ()
    def localAddress = throw closed
    override def isOpen = effect (false)
    override def closedByShutdown = effect (false)
  }

  protected trait AbstractClosedByShutdown extends AbstractClosedByClose {
    override def closedByShutdown = effect (true)
  }}
