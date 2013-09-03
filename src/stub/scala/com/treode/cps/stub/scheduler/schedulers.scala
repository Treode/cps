package com.treode.cps.stub.scheduler

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ForkJoinPool, ScheduledThreadPoolExecutor}
import scala.util.Random
import scala.util.continuations.reset
import com.treode.cps.thunk
import com.treode.cps.scheduler.{Scheduler, SchedulerConfig}

private trait TestSchedulerConfig extends SchedulerConfig {

  def await [A] (s: TestScheduler, k: => A @thunk): A

  /** Run until the condition is false; this keeps the system going through quiet periods waiting
    * for responses on sockets, timers to expire and so on.  It is only meaningful in
    * multithreaded mode.
    */
  def run (cond: => Boolean)

  /** Run while a condition is true; this causes the system to stop before all tasks and timers
    * are complete.  It is only meaningful in single threaded mode.
    */
  def whilst (cond: => Boolean)

  /** Shutdown the scheduler and cleanup threads, if any. */
  def shutdown()
}

class TestScheduler private [scheduler] (cfg: TestSchedulerConfig) extends Scheduler (cfg) {

  override def await [A] (k: => A @thunk): A = cfg.await (this, k)

  def _await [A] (k: => A @thunk): A = super.await (k)

  /** In multithreaded mode, run until the condition is false; this keeps the system going through
    * quiet periods waiting for responses on sockets, timers to expire and so on.  In single
    * threaded mode, ignore the condition and run until there are no more tasks.
    */
  def run (cond: => Boolean): Unit = cfg.run (cond)

  /** Run until no more tasks, new ones may be enqueued while running.  May be called multiple
    * times.
    */
  def run(): Unit = cfg.run (false)

  /** In single threaded mode, run while a condition is true; this causes the system to stop
    * before all tasks and timers are complete.  In multithreaded mode, this is the same as
    * `run (!cond)`.
    */
  def whilst (cond: => Boolean): Unit = cfg.whilst (cond)

  /** Shutdown the scheduler and cleanup threads, if any. */
  def shutdown() = cfg.shutdown()
}

/** Run one task at a time in one thread, choosing the task first in first out. */
private class SequentialConfig (timers: Boolean) extends TestSchedulerConfig {

  private [this] var exception = None: Option [Throwable]

  private val stub =
    if (timers)
      new SequentialStub with TimerfulStub
    else
      new SequentialStub with TimerlessStub
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
    run (v == null)
    v
  }

  def run (cond: => Boolean) {
    while (exception == None && !executor.isQuiet)
      executor.executeOne()
    exception  match {
      case None => ()
      case Some (e) => exception = None; throw e
    }}

  def whilst (cond: => Boolean) {
    while (exception == None && !executor.isQuiet && cond)
      executor.executeOne()
    exception  match {
      case None => ()
      case Some (e) => exception = None; throw e
    }}

  def shutdown() = ()
}

/** Run one task at a time in one thread, choosing the task randomly. */
private class RandomConfig (r: Random, timers: Boolean) extends TestSchedulerConfig {

  private [this] var exception = None: Option [Throwable]

  val random = r

  private val stub =
    if (timers)
      new RandomStub (random) with TimerfulStub
    else
      new RandomStub (random) with TimerlessStub
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
    run (v == null)
    v
  }

  def run (cond: => Boolean) {
    while (exception == None && !executor.isQuiet)
      executor.executeOne()
    exception  match {
      case None => ()
      case Some (e) => exception = None; throw e
    }}

  def whilst (cond: => Boolean) {
    while (exception == None && !executor.isQuiet && cond)
      executor.executeOne()
    exception  match {
      case None => ()
      case Some (e) => exception = None; throw e
    }}

  def shutdown() = ()
}

private class MultithreadedConfig (cond: => Boolean) extends TestSchedulerConfig {

  val exception = new AtomicReference (None: Option [Throwable])

  val executor = new ForkJoinPool (
      Runtime.getRuntime ().availableProcessors (),
      ForkJoinPool.defaultForkJoinWorkerThreadFactory,
      null,
      true)

  val timer = new ScheduledThreadPoolExecutor (1)

  def handleUncaughtException (e: Throwable) =
    exception.compareAndSet (None, Some (e))

  def makeThump (s: Scheduler, k: () => Any) =
    SchedulerConfig.makeSafeThump (s, k)

  def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
    SchedulerConfig.makeSafeThunk (s, k)

  def await [A] (s: TestScheduler, k: => A @thunk): A = s._await (k)

  def run (cond: => Boolean) {
    Thread.sleep (100)
    while (exception.get () == None && cond)
      Thread.sleep (100)
    while (exception.get () == None && !executor.isQuiescent)
      Thread.sleep (100)
    exception.get () match {
      case None => ()
      case Some (e) => exception.set (None); throw e
    }}

  def whilst (cond: => Boolean) = run (!cond)

  def shutdown() {
    executor.shutdownNow()
    timer.shutdownNow()
  }}

object TestScheduler {

  /** A single-threaded scheduler that selects tasks in FIFO order; if timers is false then tasks
    * submitted via `schedule` will be ignored.
    */
  def sequential (timers: Boolean = true): TestScheduler =
    new TestScheduler (new SequentialConfig (timers))

  /** A single-threaded scheduler that selects tasks in psuedo-random order; if timers is false
    * then tasks submitted via `schedule` will be ignored.
    */
  def random (seed: Long): TestScheduler =
    new TestScheduler (new RandomConfig (new Random (seed), true))

  /** A single-threaded scheduler that selects tasks in psuedo-random order; if timers is false
    * then tasks submitted via `schedule` will be ignored.
    */
  def random (seed: Long, timers: Boolean): TestScheduler =
    new TestScheduler (new RandomConfig (new Random (seed), timers))

  /** A single-threaded scheduler that selects tasks in psuedo-random order; if timers is false
    * then tasks submitted via `schedule` will be ignored.
    */
  def random (random: Random = Random, timers: Boolean = true): TestScheduler =
    new TestScheduler (new RandomConfig (random, timers))

  /** By default, run until the condition is false, that is use `cond` for `run()`. */
  def multithreaded (cond: => Boolean): TestScheduler =
    new TestScheduler (new MultithreadedConfig (cond))
}
