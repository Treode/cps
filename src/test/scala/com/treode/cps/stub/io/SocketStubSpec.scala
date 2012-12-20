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

import java.nio.ByteBuffer
import java.nio.channels._
import org.scalatest.{FlatSpec, PropSpec, Specs}
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.PropertyChecks
import scala.util.Random
import com.treode.cps.scalatest.{CpsFlatSpec, CpsPropSpec}
import com.treode.cps.io.{SocketBehaviors, SocketChecks}
import com.treode.cps.stub.scheduler.TestScheduler
import com.treode.cps.sync.Future

import Future.start

class SocketStubSpec extends Specs (SocketStubBehaviors, SocketStubProperties)

private object SocketStubBehaviors extends CpsFlatSpec with SocketBehaviors {

  class StubSpecKit extends SocketSpecKit {
    implicit val random = new scala.util.Random (0)
    implicit val scheduler = TestScheduler.sequential ()
    def newServerAddress() = SocketAddressStub ()
    def newSocket() = new SocketStub ()
    def newServerSocket() = new ServerSocketStub ()
  }

  "A server socket stub" should behave like aServerSocket (() => new StubSpecKit)

  "A socket stub" should behave like aSocket (() => new StubSpecKit)

  // We cannot force the kernel to pend connections on live sockets.
  it should "refuse connect when a connection is pending" in {
    val kit = new StubSpecKit
    import kit.{newServerAddress, newServerSocket, newSocket, scheduler}
    import kit.scheduler.spawn

    val server = newServerSocket
    server.bind (newServerAddress, 0)
    val address = server.localAddress.get
    spawn {
      val s = newSocket
      val thrown1 = start {
        val thrown = interceptCps [Exception] (s.connect (address))
        s.close ()
        thrown
      }
      val thrown2 = start {
        val thrown = interceptCps [Exception] (s.connect (address))
        s.close ()
        thrown
      }
      val e1 = thrown1.get
      val e2 = thrown2.get
      assert ((Seq (e1, e2) exists (_.isInstanceOf [ConnectionPendingException])))
      assert ((Seq (e1, e2) exists (_.isInstanceOf [AsynchronousCloseException])))
    }
    kit.run (false)
  }

  it should "throw an exception from connect when shutdown" in {
    val kit = new StubSpecKit
    import kit.{newServerAddress, newSocket}
    import kit.scheduler.spawn

    spawn {
      val s = newSocket
      interceptCps [AsynchronousCloseException] (s.connect (newServerAddress))
    }
    kit.run (false)
  }

  // This one hangs on Linux's live implementation, which is not helpful.
  it should "require connect before read" in {
    val kit = new StubSpecKit
    import kit.newSocket
    import kit.scheduler.spawn

    spawn {
      val s = newSocket
      interceptCps [NotYetConnectedException] (s.read (ByteBuffer.allocate (16)))
      s.close ()
    }
    kit.run (true)
  }

  // This one passes/fails in a flakey way on Linux's live implementation.
  it should "throw an exception from read when shutdown" in {
    val kit = new StubSpecKit
    import kit.{newServer, newSocket}
    import kit.scheduler.spawn

    val address = newServer ()
    spawn {
      val s = newSocket
      s.connect (address)
      interceptCps [AsynchronousCloseException] (s.read (ByteBuffer.allocate (16)))
    }
    kit.run (false)
  }}

private object SocketStubProperties extends CpsPropSpec with SocketChecks {

  class StubSpecKit (implicit random: Random) extends SocketSpecKit {
    implicit val scheduler = TestScheduler.random (random)
    def newServerAddress() = SocketAddressStub ()
    def newSocket() = new SocketStub ()
    def newServerSocket() = new ServerSocketStub ()
  }

  property ("SocketStubs open, connect, send and receive") {
    forAll (seeds) { seed: Long =>
      checkOpenConnectWriteRead (new StubSpecKit () (new Random (seed)))
    }}}
