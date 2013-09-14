package com.treode.cps.stub.scheduler

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ForkJoinPool, ScheduledThreadPoolExecutor}
import scala.util.Random
import scala.util.continuations.reset
import com.treode.cps.thunk
import com.treode.cps.scheduler.{Scheduler, SchedulerConfig}

private trait TestSchedulerConfig extends SchedulerConfig {

  /** Implement Scheduler.await appropriately for this test scheduler. */
  def await [A] (s: TestScheduler, k: => A @thunk): A

  /** Run until the condition is false or until there are no more tasks; include scheduled tasks
    * if `timers` is true.
    */
  def run (cond: => Boolean, timers: Boolean)

  /** Shutdown the scheduler and cleanup threads. */
  def shutdown()
}

class TestScheduler private [scheduler] (cfg: TestSchedulerConfig) extends Scheduler (cfg) {

  override def await [A] (k: => A @thunk): A = cfg.await (this, k)

  def _await [A] (k: => A @thunk): A = super.await (k)

  /** Run until the condition is false or until there are no more tasks.  New tasks may be enqueued
    * while running, and this may be called multiple times.  Include timers in the run if
    * indicated, otherwise ignore any scheduled tasks.
    */
  def run (cond: => Boolean = true, timers: Boolean = true): Unit = cfg.run (cond, timers)

  /** Shutdown the scheduler and cleanup threads, if any. */
  def shutdown() = cfg.shutdown()
}

/** Run one task at a time in one thread, choosing the task first in first out. */
private class SequentialConfig extends TestSchedulerConfig {

  private [this] var exception = None: Option [Throwable]

  private val stub = new SequentialStub
  val timer = stub
  val executor = stub

  def handleUncaughtException (e: Throwable)  =  exception = Some (e)

  def makeThump (s: Scheduler, k: () => Any) =
    SchedulerConfig.makeSafeThump (s, k)

  def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
    SchedulerConfig.makeSafeThunk (s, k)

  def await [A] (s: TestScheduler, k: => A @thunk): A = {
    var v: A = null .asInstanceOf [A]
    reset {
      val _t1 = k
      v = _t1
    }
    run (v == null, false)
    v
  }

  def run (cond: => Boolean, timers: Boolean) {
    while (exception == None && !executor.isQuiet (timers) && cond)
      executor.executeOne (timers)
    exception  match {
      case None => ()
      case Some (e) => exception = None; throw e
    }}

  def shutdown() = ()
}

/** Run one task at a time in one thread, choosing the task randomly. */
private class RandomConfig (r: Random) extends TestSchedulerConfig {

  private [this] var exception = None: Option [Throwable]

  val random = r

  private val stub = new RandomStub (random)
  val timer = stub
  val executor = stub

  def handleUncaughtException (e: Throwable)  =  exception = Some (e)

  def makeThump (s: Scheduler, k: () => Any) =
    SchedulerConfig.makeSafeThump (s, k)

  def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
    SchedulerConfig.makeSafeThunk (s, k)

  def await [A] (s: TestScheduler, k: => A @thunk): A = {
    var v: A = null .asInstanceOf [A]
    reset {
      val _t1 = k
      v = _t1
    }
    run (v == null, false)
    v
  }

  def run (cond: => Boolean, timers: Boolean) {
    while (exception == None && !executor.isQuiet (timers) && cond)
      executor.executeOne (timers)
    exception  match {
      case None => ()
      case Some (e) => exception = None; throw e
    }}

  def shutdown() = ()
}

private class MultithreadedConfig extends TestSchedulerConfig {

  val exception = new AtomicReference (None: Option [Throwable])

  val executor = new ForkJoinPool (
      Runtime.getRuntime ().availableProcessors (),
      ForkJoinPool.defaultForkJoinWorkerThreadFactory,
      null,
      true)

  val timer = new ScheduledThreadPoolExecutor (1)

  private def isQuiet =
    executor.isQuiescent && (timer.getCompletedTaskCount - timer.getTaskCount == 0)

  def handleUncaughtException (e: Throwable) =
    exception.compareAndSet (None, Some (e))

  def makeThump (s: Scheduler, k: () => Any) =
    SchedulerConfig.makeSafeThump (s, k)

  def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
    SchedulerConfig.makeSafeThunk (s, k)

  def await [A] (s: TestScheduler, k: => A @thunk): A = s._await (k)

  def run (cond: => Boolean, timers: Boolean) {
    Thread.sleep (100)
    while (exception.get () == None && !isQuiet && cond)
      Thread.sleep (100)
    exception.get () match {
      case None => ()
      case Some (e) => exception.set (None); throw e
    }}

  def shutdown() {
    executor.shutdownNow()
    timer.shutdownNow()
  }}

object TestScheduler {

  /** A single-threaded scheduler that selects tasks in FIFO order.  The value `timers` for
    * `timers` serves as a default for TestScheduler.run
    */
  def sequential(): TestScheduler =
    new TestScheduler (new SequentialConfig)

  /** A single-threaded scheduler that selects tasks in psuedo-random order. */
  def random (seed: Long): TestScheduler =
    new TestScheduler (new RandomConfig (new Random (seed)))

  /** A single-threaded scheduler that selects tasks in psuedo-random order. */
  def random (random: Random = Random): TestScheduler =
    new TestScheduler (new RandomConfig (random))

  /** By default, run until the condition is false, that is use `cond` for `run()`. */
  def multithreaded(): TestScheduler =
    new TestScheduler (new MultithreadedConfig)
}
