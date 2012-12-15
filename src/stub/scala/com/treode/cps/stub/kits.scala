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

package com.treode.cps.stub

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ForkJoinPool, ScheduledThreadPoolExecutor}
import scala.util.Random
import com.treode.cps.{CpsKit, CpsSocketKit}
import com.treode.cps.scheduler.{Scheduler, SchedulerConfig}
import com.treode.cps.stub.io.{ServerSocketStub, SocketStub}
import com.treode.cps.stub.scheduler.ExecutorStub

trait CpsSpecKit extends CpsKit {

  /** Run until no more tasks, new ones may be enqueued while running.  May be called multiple
     * times.
     */
  def run()

  /** Shutdown the scheduler and cleanup threads, if any. */
  def shutdown()
}

object CpsSpecKit {

  /** Run multiple tasks simultaneously in parallel threads. */
  class Multithreaded (cond: => Boolean) extends CpsSpecKit {

    private val executor = new ForkJoinPool (
          Runtime.getRuntime ().availableProcessors (),
          ForkJoinPool.defaultForkJoinWorkerThreadFactory,
          null,
          true)

    private [this] val exception = new AtomicReference (None: Option [Throwable])

    val config = new SchedulerConfig {

      val executor = Multithreaded.this.executor

      val timer = new ScheduledThreadPoolExecutor (1)

      def handleUncaughtException (e: Throwable) = exception.compareAndSet (None, Some (e))

      def makeThump (s: Scheduler, k: () => Any) =
        SchedulerConfig.makeSafeThump (s, k)

      def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
        SchedulerConfig.makeSafeThunk (s, k)
    }

    implicit val scheduler = Scheduler (config)

    private def runToException (cond: => Boolean) {
      Thread.sleep (100)
      while (exception.get () == None && cond)
        Thread.sleep (100)
      while (exception.get () == None && !config.executor.isQuiescent)
        Thread.sleep (100)
      exception.get () match {
        case None => ()
        case Some (e) => exception.set (None); throw e
      }}

    def run(): Unit = runToException (cond)

    /** Run until the condition is false; this keeps the system going through quiet periods waiting
      * for responses on sockets, timers to expire and so on.
      */
    def run (cond: => Boolean) = runToException (cond)

    def shutdown() {
      config.executor.shutdownNow()
      config.timer.shutdownNow()
    }}

  def newMultihreadedKit (cond: => Boolean) = new Multithreaded (cond)

  /** Run one task at a time in one thread, choosing the task first in first out. */
  class Sequential extends CpsSpecKit {

    private [this] var exception = None: Option [Throwable]

    val config = new SchedulerConfig {

      val timer = ExecutorStub.newSequentialExecutor
      val executor = timer

      def handleUncaughtException (e: Throwable)  =  exception = Some (e)

      def makeThump (s: Scheduler, k: () => Any) =
        SchedulerConfig.makeSafeThump (s, k)

      def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
        SchedulerConfig.makeSafeThunk (s, k)
    }

    implicit val scheduler = Scheduler (config)

    private def runToException (cond: => Boolean) {
      while (exception == None && (cond || !config.executor.isQuiet))
        config.executor.executeOne()
      exception  match {
        case None => ()
        case Some (e) => exception = None; throw e
      }}

    def run(): Unit = runToException (false)

    def shutdown() = ()
  }

  def newSequentialKit = new Sequential

  /** Run one task at a time in one thread, choosing the task randomly. */
  class RandomKit (r: Random) extends CpsSpecKit {

    private [this] var exception = None: Option [Throwable]

    implicit val random = r

    val config = new SchedulerConfig {

      val timer = ExecutorStub.newRandomExecutor (random)
      val executor = timer

      def handleUncaughtException (e: Throwable)  =  exception = Some (e)

      def makeThump (s: Scheduler, k: () => Any) =
        SchedulerConfig.makeSafeThump (s, k)

      def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
        SchedulerConfig.makeSafeThunk (s, k)
    }

    implicit val scheduler = Scheduler (config)

    private def runToException (cond: => Boolean) {
      while (exception == None && (cond || !config.executor.isQuiet))
        config.executor.executeOne()
      exception  match {
        case None => ()
        case Some (e) => exception = None; throw e
      }}

    def run(): Unit = runToException (false)

    def shutdown() = ()
  }

  def newRandomKit (random: Random): RandomKit = new RandomKit (random) {}
  def newRandomKit (seed: Long): RandomKit = new RandomKit (new Random (seed)) {}
}

trait CpsStubSocketKit extends CpsSocketKit {
  this: CpsKit =>

  def newSocket = SocketStub ()
  def newServerSocket = ServerSocketStub ()
}
