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

package com.treode.cps
package io

import java.net.{SocketAddress, SocketOption}
import java.nio.ByteBuffer
import javax.net.ssl.{SSLContext, SSLEngine}
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.HandshakeStatus._
import javax.net.ssl.SSLEngineResult.Status._
import com.treode.cps.scheduler.Scheduler
import com.treode.cps.sync.Lock

trait SecureSocket extends Socket

trait SecureServerSocket extends ServerSocket {
  def accept (): SecureSocket @thunk
}

private class SecureSocketLive (socket: Socket, engine: SSLEngine) (
    implicit scheduler: Scheduler) extends SecureSocket {

  private [this] val empty = ByteBuffer.allocate (0)

  private [this] var netIn = ByteBuffer.allocate (engine.getSession.getPacketBufferSize)
  private [this] var netOut = ByteBuffer.allocate (engine.getSession.getPacketBufferSize)
  private [this] var appIn = empty

  private [this] val inLock = Lock ()
  private [this] val outLock = Lock ()

  /** Grow the buffer if necessary. */
  private [this] def grow (b0: ByteBuffer, n: Int): ByteBuffer = {
    if (b0.remaining < n) {
      // Handshake and negotiation may have modified the size hint.  Allocate a new larger buffer
      // and move any data from the old one to the beginning of the new one.
      val b1 = ByteBuffer.allocate (b0.position + n)
      b0.flip ()
      b1.put (b0)
      b1
    } else {
      b0
    }}

  /** Undo ByteBuffer.flip () */
  private [this] def unflip (b: ByteBuffer) {
    b.position (b.limit)
    b.limit (b.capacity)
  }

  /** Write out the entire buffer. */
  private [this] def flush (src: ByteBuffer) = {
    src.flip ()
    var n = 0
    while (src.hasRemaining && n >= 0) {
      n = socket.write (src)
    }
    if (n >= 0)  src.compact ()
  }

  /** Copy as much as possible from appIn to dst buffer. */
  private [this] def transfer (dst: ByteBuffer): Int = {
    if (appIn.remaining <= dst.remaining) {
      val len = appIn.remaining
      dst.put (appIn)
      appIn = empty
      len
    } else {
      val len = dst.remaining
      val slice = appIn.slice ()
      slice.limit (len)
      dst.put (slice)
      appIn.position (appIn.position + len)
      len
    }}

  private [this] def total (bs: Array [ByteBuffer]) =
    bs .map (_.position) .sum

  /** Copy as much as possible from appIn to dst buffers. */
  private [this] def transfer (dst: Array [ByteBuffer]): Int = {
    var len = 0
    var i = 0
    while (i < dst.length && appIn.hasRemaining) {
      len += transfer (dst (i))
      i += 1
    }
    len
  }

  /** The execution of one user read or write, which may involved performing several network
   * read and writes.  The same consumed, produced, src and dst are used throughout these multiple
   * network operations.
   */
  private class Op (src: Array [ByteBuffer], dst: Array [ByteBuffer]) {

    private [this] def handshake(): Unit @thunk = {
      engine.getHandshakeStatus match {
        case NOT_HANDSHAKING => cut()
        case FINISHED => cut()
        case NEED_TASK => runTasks ()
        case NEED_WRAP => wrap(); ()
        case NEED_UNWRAP => unwrap(); ()
      }}

    private [this] def runTasks (): Unit @thunk = {
      var t = engine.getDelegatedTask
      while (t != null) {
        t.run ()
        t = engine.getDelegatedTask
      }
      handshake()
    }

    def _unwrap (more: Boolean) = {
      var n = if (netIn.position == 0 || more) socket.read (netIn) else cut (0)
      if (n < 0) engine.closeInbound ()
      netIn.flip ()
      unflip (appIn)
      val res = engine.unwrap (netIn, appIn)

      res.getStatus match {
        case BUFFER_OVERFLOW =>
          unflip (netIn)
          appIn = grow (appIn, engine.getSession.getApplicationBufferSize)

        case BUFFER_UNDERFLOW =>
          unflip (netIn)
          netIn = grow (netIn, engine.getSession.getPacketBufferSize)

        case CLOSED =>
          unflip (netIn)

        case OK =>
          netIn.compact ()
      }

      appIn.flip ()
      res.getStatus == BUFFER_UNDERFLOW
    }

    def unwrap(): Unit @thunk = {
      val unwrapped = inLock.exclusive {
        if (appIn.hasRemaining) {
          transfer (dst)
          false
        } else {
          var more = _unwrap (false)
          while (more || engine.getHandshakeStatus == NEED_UNWRAP)
            more = _unwrap (more)
            true
        }}
      if (unwrapped)
        handshake()
      else
        cut()
    }

    def wrap(): Unit @thunk = {
      val status = outLock.exclusive {
        val res = engine.wrap (src, netOut)
        flush (netOut)

        res.getStatus match {

          case BUFFER_OVERFLOW =>
            netOut = grow (netOut, engine.getSession.getPacketBufferSize)

          case BUFFER_UNDERFLOW =>
            // We expect the SSL engine to handle any amount of application data.
            throw new IllegalStateException

          case CLOSED => ()
          case OK => ()
        }}

      handshake()
    }}

  def close (): Unit @thunk = {
    engine.closeOutbound ()
    new Op (Array (), Array ()) .wrap()
    socket.close ()
  }

  def isOpen = socket.isOpen

  def getOption [T] (name: SocketOption [T]) = socket.getOption (name)

  def localAddress = socket.localAddress

  def setOption [T] (name: SocketOption [T], value: T) = {
    socket.setOption (name, value)
    this
  }

  def supportedOptions = socket.supportedOptions

  def connect (remote: SocketAddress) = socket.connect (remote)

  def remoteAddress = socket.remoteAddress

  private def _read (dst: Array [ByteBuffer]) = {
    val m = total (dst)
    new Op (Array (), dst) .unwrap()
    val n = total (dst) - m
    if (n == 0 && !socket.isOpen) -1 else n
  }

  def read (dst: ByteBuffer): Int @thunk = _read (Array (dst))

  def read (dst: Array [ByteBuffer]): Long @thunk = _read (dst) .toLong

  private def _write (src: Array [ByteBuffer]) = {
    val m = total (src)
    new Op (src, Array ()) .wrap()
    val n = total (src) - m
    if (n == 0 && !socket.isOpen) -1 else n
  }

  def write (src: ByteBuffer): Int @thunk = _write (Array (src))

  def write (src: Array [ByteBuffer]): Long @thunk = _write (src) .toLong
}

object SecureSocket {

  def apply (channel: Socket, context: SSLContext, client: Boolean) (
      implicit scheduler: Scheduler): SecureSocket = {
    val engine = context.createSSLEngine
    engine.setUseClientMode (client)
    new SecureSocketLive (channel, engine)
  }}

private class SecureServerSocketLive (
    socket: ServerSocket, context: SSLContext) (implicit scheduler: Scheduler)
extends SecureServerSocket {

  def close() = socket.close()

  def isOpen = socket.isOpen

  def getOption [T] (name: SocketOption [T]) = socket.getOption (name)

  def localAddress = socket.localAddress

  def setOption [T] (name: SocketOption [T], value: T) = {
    socket.setOption (name, value)
    this
  }

  def supportedOptions = socket.supportedOptions

  def accept (): SecureSocket @thunk = {
    val client = socket.accept()
    SecureSocket (client, context, false)
  }

  def bind (local: SocketAddress, backlog: Int = 0) = {
    socket.bind (local, backlog)
    this
  }}

object SecureServerSocket {

  def apply (channel: ServerSocket, context: SSLContext) (
      implicit scheduler: Scheduler): SecureServerSocket =
    new SecureServerSocketLive (channel, context)
}
