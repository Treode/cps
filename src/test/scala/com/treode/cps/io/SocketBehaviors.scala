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

package com.treode.cps.io

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels._
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import com.treode.cps.scalatest.CpsFlatSpec
import com.treode.cps.scheduler.Scheduler
import com.treode.cps.sync.Future.start

trait SocketBehaviors extends SocketChecks {
  this: CpsFlatSpec =>

  protected [this] def aServerSocket (factory: () => SocketSpecKit) = {
    it should "refuse bind when already bound" in {
      val kit = factory ()
      import kit.{newServerAddress, newServerSocket}
      import kit.scheduler.spawn

      spawn {
        val s = newServerSocket ()
        s.bind (newServerAddress ())
        intercept [AlreadyBoundException] {
          s.bind (newServerAddress ())
        }
        s.close ()
      }
      kit.run (false)
    }

    it should "refuse bind when closed" in {
      val kit = factory ()
      import kit.{newServerAddress, newServerSocket}
      import kit.scheduler.spawn

      spawn {
        val s = newServerSocket ()
        s.close ()
        intercept [ClosedChannelException] (s.bind (newServerAddress ()))
      }
      kit.run (false)
    }

    it should "require bind before accept" in {
      val kit = factory ()
      import kit.newServerSocket
      import kit.scheduler.spawn

      spawn {
        val s = newServerSocket ()
        interceptCps [NotYetBoundException] (s.accept ())
        s.close ()
      }
      kit.run (false)
    }

    it should "refuse accept when closed" in {
      val kit = factory ()
      import kit.{newServerAddress, newServerSocket}
      import kit.scheduler.spawn

      spawn {
        val s = newServerSocket ()
        s.bind (newServerAddress ())
        s.close ()
        interceptCps [ClosedChannelException] (s.accept ())
      }
      kit.run (false)
    }

    it should "refuse an accept when one is already pending" in {
      val kit = factory ()
      import kit.{newServerAddress, newServerSocket, newSocket, scheduler}
      import kit.scheduler.spawn

      spawn {
        val s = newServerSocket ()
        s.bind (newServerAddress ())
        val thrown1 = start {
          val thrown = interceptCps [Exception] (s.accept ())
          s.close ()
          thrown
        }
        val thrown2 = start {
          val thrown = interceptCps [Exception] (s.accept ())
          s.close ()
          thrown
        }
        val e1 = thrown1.get
        val e2 = thrown2.get
        assert ((Seq (e1, e2) exists (_.isInstanceOf [AcceptPendingException])))
        assert ((Seq (e1, e2) exists (_.isInstanceOf [AsynchronousCloseException])))
      }
      kit.run (false)
    }}

  protected [this] def aSocket (factory: () => SocketSpecKit) = {
    it should "refuse connect when already connected" in {
      val kit = factory ()
      import kit.{newServer, newSocket}
      import kit.scheduler.spawn

      val address = newServer ()
      spawn {
        val s = newSocket ()
        s.connect (address)
        interceptCps [AlreadyConnectedException] (s.connect (address))
        //s.close ()
      }
      kit.run (false)
    }

    it should "refuse connect when closed" in {
      val kit = factory ()
      import kit.{newServerAddress, newSocket}
      import kit.scheduler.spawn

      spawn {
        val s = newSocket ()
        s.close ()
        interceptCps [ClosedChannelException] (s.connect (newServerAddress ()))
      }
      kit.run (false)
    }

    it should "refuse read when closed" in {
      val kit = factory ()
      import kit.newSocket
      import kit.scheduler.spawn

      spawn {
        val s = newSocket ()
        s.close ()
        interceptCps [ClosedChannelException] (s.read (ByteBuffer.allocate (16)))
      }
      kit.run (false)
    }

    it should "refuse read when a read is pending" in {
      val kit = factory ()
      import kit.{newServer, newSocket, scheduler}
      import kit.scheduler.spawn

      val address = newServer ()
      spawn {
        val s = newSocket ()
        s.connect (address)
        val thrown1 = start {
          val thrown = interceptCps [Exception] (s.read (ByteBuffer.allocate (16)))
          s.close ()
          thrown
        }
        val thrown2 = start {
          val thrown = interceptCps [Exception] (s.read (ByteBuffer.allocate (16)))
          s.close ()
          thrown
        }
        val e1 = thrown1.get
        val e2 = thrown2.get
        assert ((Seq (e1, e2) exists (_.isInstanceOf [ReadPendingException])))
        assert ((Seq (e1, e2) exists (_.isInstanceOf [AsynchronousCloseException])))
      }
      kit.run (false)
    }

    it should "require connect before write" in {
      val kit = factory ()
      import kit.{newServer, newSocket}
      import kit.scheduler.spawn

      spawn {
        val s = newSocket ()
        interceptCps [NotYetConnectedException] (s.write (ByteBuffer.allocate (16)))
      }
      kit.run (false)
    }

    it should "refuse write when closed" in {
      val kit = factory ()
      import kit.{newServer, newSocket}
      import kit.scheduler.spawn
      spawn {
        val s = newSocket ()
        s.close ()
        interceptCps [ClosedChannelException] (s.write (ByteBuffer.allocate (16)))
      }
      kit.run (false)
    }

    it should "close a read when the peer closes" in {
      val kit = factory ()
      import kit.{newServerAddress, newServerSocket, newSocket}
      import kit.scheduler.spawn

      val s = newServerSocket ()
      s.bind (newServerAddress ())
      spawn {
        val c = s.accept()
        c.close()
      }

      var mark = false
      spawn {
        val c = newSocket
        c.connect (s.localAddress.get)
        expectResult (-1) (c.read (ByteBuffer.allocate (16)))
        mark = true
      }
      kit.run (true)
      assert (mark)
    }}}
