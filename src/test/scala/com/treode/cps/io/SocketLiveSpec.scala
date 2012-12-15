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

package com.treode.cps.io

import java.net.{SocketAddress, InetSocketAddress}
import java.nio.channels.{AsynchronousChannelGroup => JGroup}
import java.util.concurrent.ThreadFactory
import com.treode.cps.scalatest.CpsFlatSpec
import com.treode.cps.stub.CpsSpecKit

class SocketLiveSpec extends CpsFlatSpec with SocketBehaviors {

  class LiveSpecKit extends CpsSpecKit.Multithreaded (false) with SocketSpecKit {

    private [this] val threads = new ThreadFactory {
      def newThread (r: Runnable) = new Thread (r, "Channels")
    }

    private [this] implicit val group = JGroup.withFixedThreadPool (1, threads)

    def newServerAddress () = new InetSocketAddress (0)
    def newSocket(): Socket = SocketLive ()
    def newServerSocket(): ServerSocket = ServerSocketLive ()
  }

  "A live server socket" should behave like aServerSocket (() => new LiveSpecKit)

  "A live socket" should behave like aSocket (() => new LiveSpecKit)

  "Live sockets" should "open, connect, send and receive" in {
    checkOpenConnectWriteRead (new LiveSpecKit)
  }}
