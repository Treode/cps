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

/** Map between Scala's Unit and Java's Void. */
private class VoidHandler (t: Thunk [Unit]) extends CompletionHandler [Void, Void] {
  def completed (result: Void, attachement: Void): Unit = t (result)
  def failed (thrown: Throwable, attachment: Void): Unit = t.fail (thrown)
}

/** Map between Scala's Int and Java's Integer. */
private class IntHandler (t: Thunk [Int]) extends CompletionHandler [java.lang.Integer, Void] {
  def completed (result: java.lang.Integer, attachement: Void): Unit = t (result)
  def failed (thrown: Throwable, attachment: Void): Unit = t.fail (thrown)
}

/** Map between Scala's Int and Java's Integer. */
private class LongHandler (t: Thunk [Long]) extends CompletionHandler [java.lang.Long, Void] {
  def completed (result: java.lang.Long, attachement: Void): Unit = t (result)
  def failed (thrown: Throwable, attachment: Void): Unit = t.fail (thrown)
}

private class SocketHandler (s: Scheduler, t: Thunk [Socket]) extends CompletionHandler [JSocket, Void] {
  def completed (result: JSocket, attachement: Void): Unit = t (new SocketLive (s, result))
  def failed (thrown: Throwable, attachment: Void): Unit = t.fail (thrown)
}

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
    suspend {t: Thunk [Unit] => socket.connect (remote, null, new VoidHandler (t))}

  def remoteAddress: Option [SocketAddress] = {
    val a = socket.getRemoteAddress ()
    if (a == null) None else Some (a)
  }

  def read (dst: ByteBuffer): Int @thunk =
    suspend {t: Thunk [Int] => socket.read (dst, null, new IntHandler (t))}

  def read (dst: Array [ByteBuffer]): Long @thunk =
    suspend {t: Thunk [Long] => socket.read (dst, 0, dst.length, 0, MILLISECONDS, null, new LongHandler (t))}

  def write (src: ByteBuffer): Int @thunk =
    suspend {t: Thunk [Int] => socket.write (src, null, new IntHandler (t))}

  def write (src: Array [ByteBuffer]): Long @thunk =
    suspend {t: Thunk [Long] => socket.write (src, 0, src.length, 0, MILLISECONDS, null, new LongHandler (t))}
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

  def accept (): Socket @thunk = {
    suspend { t: Thunk [Socket] =>
      socket.accept (null, new SocketHandler (scheduler, t))
    }}

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
