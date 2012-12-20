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

import java.lang.{Integer => JInteger, Long => JLong}
import java.net.{SocketAddress, SocketOption}
import java.nio.ByteBuffer
import java.nio.channels.{
  AsynchronousChannelGroup => JGroup,
  AsynchronousServerSocketChannel => JServerSocket,
  AsynchronousSocketChannel => JSocket,
  NetworkChannel => JNetworkChannel,
  CompletionHandler}
import java.util.concurrent.{ThreadFactory, TimeUnit}
import scala.collection.JavaConversions._
import com.treode.cps.scheduler.Scheduler

import TimeUnit.MILLISECONDS

object CompletionHandlers {

  val unit = new CompletionHandler [Void, Thunk [Unit]] {
    def completed (v: Void, k: Thunk [Unit]) = k()
    def failed (t: Throwable, k: Thunk [Unit]) = k.fail (t)
  }

  val int = new CompletionHandler [JInteger, Thunk [Int]] {
    def completed (v: JInteger, k: Thunk [Int]) = k (v)
    def failed (t: Throwable, k: Thunk [Int]) = k.fail (t)
  }

  val long = new CompletionHandler [JLong, Thunk [Long]] {
    def completed (v: JLong, k: Thunk [Long]) = k (v)
    def failed (t: Throwable, k: Thunk [Long]) = k.fail (t)
  }

  def socket (s: Scheduler) = new CompletionHandler [JSocket, Thunk [Socket]] {
    def completed (v: JSocket, k: Thunk [Socket]) = k (new SocketLive (s, v))
    def failed (t: Throwable, k: Thunk [Socket]) = k.fail (t)
  }}

private trait AbstractSocketLive extends NetworkChannel {

  protected [this] val socket: JNetworkChannel

  def close (): Unit @thunk = cut (socket.close ())

  def getOption [A] (name: SocketOption [A]): A = socket.getOption (name)

  def isOpen: Boolean = socket.isOpen ()

  def localAddress: Option [SocketAddress] = {
    val a = socket.getLocalAddress ()
    if (a == null) None else Some (a)
  }

  def setOption [A] (name: SocketOption [A], value: A): this.type = {
    socket.setOption (name, value)
    this
  }

  def supportedOptions = asScalaSet (socket.supportedOptions ()).toSet
}

private class SocketLive (scheduler: Scheduler, protected val socket: JSocket)
extends AbstractSocketLive with Socket {
  import scheduler.suspend

  def connect (remote: SocketAddress): Unit @thunk =
    suspend [Unit] (socket.connect (remote, _, CompletionHandlers.unit))

  def remoteAddress: Option [SocketAddress] = {
    val a = socket.getRemoteAddress ()
    if (a == null) None else Some (a)
  }

  def read (dst: ByteBuffer): Int @thunk =
    suspend [Int] (socket.read (dst, _, CompletionHandlers.int))

  def read (dst: Array [ByteBuffer]): Long @thunk =
    suspend [Long] (socket.read (dst, 0, dst.length, 0, MILLISECONDS, _, CompletionHandlers.long))

  def write (src: ByteBuffer): Int @thunk =
    suspend [Int] (socket.write (src, _, CompletionHandlers.int))

  def write (src: Array [ByteBuffer]): Long @thunk =
    suspend [Long] (socket.write (src, 0, src.length, 0, MILLISECONDS, _, CompletionHandlers.long))
}

object SocketLive {

  /** Open a socket.
    * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousSocketChannel.html#open () open]]
    * of Java's AsynchronousSocketChannel for details.
    *
    * @param s The scheduler for continuing execution when data is received.  For these live sockets
    *     the scheduler must be able to handle multiple threads invoking execute to enqueue tasks.
    * @param g The Java AsynchronousSocketGroup in which to enroll the new socket.  The callbacks
    *     of this implementation pass the event from the OS to the CPS scheduler, so the scheduler
    *     provided to the socket group can be simple.
    */
  def apply () (implicit s: Scheduler, g: JGroup): Socket =
    new SocketLive (s, JSocket.open (g))
}

private class ServerSocketLive (scheduler: Scheduler, group: JGroup)
extends AbstractSocketLive with ServerSocket {
  import scheduler.suspend

  protected [this] val socket = JServerSocket.open (group)

  def accept (): Socket @thunk =
    suspend [Socket] (socket.accept (_, CompletionHandlers.socket (scheduler)))

  def bind (local: SocketAddress, backlog: Int = 0): this.type = {
    socket.bind (local, backlog)
    this
  }}

object ServerSocketLive {

  /** Create a new server socket.
    * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousServerSocketChannel.html#open () open]]
    * of Java's AsynchronousServerSocketChannel for details.
    *
    * @param s The scheduler for continuing execution when data is received.  For these live sockets
    *     the scheduler must be able to handle multiple threads invoking execute to enqueue tasks.
    * @param g The Java AsynchronousSocketGroup in which to enroll the new socket.  The callbacks
    *     of this implementation pass the event from the OS to the CPS scheduler, so the scheduler
    *     provided to the socket group can be simple.
    */
  def apply () (implicit s: Scheduler, g: JGroup): ServerSocket =
    new ServerSocketLive (s, g)
}
