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

import java.nio.channels.{AsynchronousChannelGroup => JGroup}
import java.util.concurrent.{ForkJoinPool, ScheduledThreadPoolExecutor, ThreadFactory}
import com.treode.cps.io.{ServerSocket, ServerSocketLive, Socket, SocketLive}
import com.treode.cps.scheduler.{Scheduler, SchedulerConfig}

trait CpsKit {

  // Making the scheduler a val allows the user to import scheduler._; however it also makes
  // initializing the kit tricky.  To resolve this, wew write classes that fill in these
  // dependencies, and those classes have constructors that take configuration parameters.
  val scheduler: Scheduler

  /** Shutdown the scheduler and sockets, and then exit the JVM. */
  def shutdown()
}

trait CpsSocketKit {
  def newSocket: Socket
  def newServerSocket: ServerSocket
}

trait CpsLiveConfig {
  val numberOfThreads = Runtime.getRuntime.availableProcessors
}

class CpsLiveKit (config: CpsLiveConfig) extends CpsKit {

  private val executor = new ForkJoinPool (
    config.numberOfThreads, // Number of threads
    ForkJoinPool.defaultForkJoinWorkerThreadFactory,
    null,
    true)

  private val timer = new ScheduledThreadPoolExecutor (1)

  val scheduler = Scheduler (new SchedulerConfig {

    val executor = CpsLiveKit.this.executor

    val timer = CpsLiveKit.this.timer

    def handleUncaughtException (exn: Throwable) =
      exn.printStackTrace

    def makeThump (s: Scheduler, k: () => Any) =
      SchedulerConfig.makeFastThump (s, k)

    def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
      SchedulerConfig.makeFastThunk (s, k)
  })

  def shutdown() {
    timer.shutdownNow()
    executor.shutdown()
  }}

trait CpsLiveSocketKit extends CpsKit with CpsSocketKit {

  private val threads = new ThreadFactory {
    def newThread (r: Runnable) = new Thread (r, "Channels")
  }

  private val group = JGroup.withFixedThreadPool (1, threads)

  def newServerSocket = ServerSocketLive (scheduler, group)
  def newSocket = SocketLive (scheduler, group)

  override abstract def shutdown() {
    group.shutdownNow()
    super.shutdown()
  }}
