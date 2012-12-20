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

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousCloseException, ShutdownChannelGroupException}
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.Assertions
import scala.collection.mutable
import com.treode.cps.stub.scheduler.TestScheduler

import com.treode.cps.sync.Future.start

trait SocketChecks {
  this: Assertions =>

  trait SocketSpecKit {

    implicit val scheduler: TestScheduler
    import scheduler.spawn

    /** Run until the system is quiet and the condition is false. */
    def run (cond: => Boolean): Unit = scheduler.run (cond)

    /** Make a SockAddress for a server. */
    def newServerAddress (): SocketAddress

    /** Make a new ServerSocket; overridden by secure socket tests. */
    def newServerSocket (): ServerSocket

    /** Make a new client Socket; overridden by secure socket tests. */
    def newSocket (): Socket

    /** Make a server that listens for connections, and return its address. */
    def newServer () = {
      val s = newServerSocket ()
      s.bind (newServerAddress ())
      spawn {
        try {
          for (s <- whilst (true) (s.accept ())) ()
        } catch {
          case e: AsynchronousCloseException => ()
          case e: ShutdownChannelGroupException => ()
        }}
      s.localAddress.get
    }}

  def checkOpenConnectWriteRead (kit: SocketSpecKit) {
    import kit.{newServerAddress, newServerSocket, newSocket, scheduler}
    import kit.scheduler.spawn

    val latch = new AtomicInteger (2)
    val log = mutable.Set [String] ()

    def writeInt (s: Socket, i: Int): Unit @thunk = {
      val buf = ByteBuffer.allocate (4)
      buf.putInt (i)
      buf.flip ()
      var n = 0
      while (n < 4) n += s.write (buf)
    }

    def writeStr (s: Socket, str: String): Unit @thunk = {
      val buf = ByteBuffer.wrap (str.getBytes)
      val len = buf.limit
      writeInt (s, len)
      var n = 0
      while (n < len) n += s.write (buf)
      expectResult (len) (n)
    }

    def readInt (s: Socket) = {
      val buf = ByteBuffer.allocate (4)
      var n = 0
      while (n < 4) n += s.read (buf)
      buf.flip ()
      val i = buf.getInt ()
      i
    }

    def readStr (s: Socket, expected: String): Unit @thunk = {
      val len = readInt (s)
      val buf = ByteBuffer.allocate (len)
      var n = 0
      while (n < len) n += s.read (buf)
      val message = new String (buf.array, 0, len)
      expectResult (expected) (message)
    }

    spawn {
      val server = newServerSocket ()
      server.bind (newServerAddress (), 3)

      spawn { // Server
        val other = server.accept ()
        server.close ()

        val r = start {
          readStr (other, "Hello Server")
          readStr (other, speech)
          assert (log.add ("server read"))
        }

        val w = start {
          writeStr (other, "Hello Client")
          writeStr (other, insight)
          assert (log.add ("server write"))
        }

        r.get
        w.get
        other.close ()
        latch.getAndDecrement ()
      }

      spawn { // Client
        val client = newSocket ()
        client.connect (server.localAddress.get)

        val r = start {
          readStr (client, "Hello Client")
          readStr (client, insight)
          assert (log.add ("client read"))
        }

        val w = start {
          writeStr (client, "Hello Server")
          writeStr (client, speech)
          assert (log.add ("client write"))
        }

        r.get
        w.get
        client.close ()
        latch.getAndDecrement ()
      }}

    kit.run (latch.get () > 0)
    expectResult (Set ("server read", "server write", "client read", "client write")) (log)
  }}
