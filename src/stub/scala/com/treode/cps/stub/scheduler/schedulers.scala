package com.treode.cps.stub.scheduler

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ForkJoinPool, ScheduledThreadPoolExecutor}
import scala.util.Random
import com.treode.cps.scheduler.{Scheduler, SchedulerConfig}

private trait TestSchedulerConfig extends SchedulerConfig {

  /** Run until the condition is false; this keeps the system going through quiet periods waiting
    * for responses on sockets, timers to expire and so on.
    */
  def run (cond: => Boolean)

  /** Shutdown the scheduler and cleanup threads, if any. */
  def shutdown()
}

class TestScheduler private [scheduler] (cfg: TestSchedulerConfig) extends Scheduler (cfg) {

  /** In multithreaded mode, run until the condition is false; this keeps the system going through
    * quiet periods waiting for responses on sockets, timers to expire and so on.  In single
    * threaded mode, ignore the condition and run until there are no more tasks.
    */
  def run (cond: => Boolean): Unit = cfg.run (cond)

  /** Run until no more tasks, new ones may be enqueued while running.  May be called multiple
    * times.
    */
  def run(): Unit = cfg.run (false)

  /** Shutdown the scheduler and cleanup threads, if any. */
  def shutdown() = cfg.shutdown()
}

/** Run one task at a time in one thread, choosing the task first in first out. */
private class SequentialConfig extends TestSchedulerConfig {

  private [this] var exception = None: Option [Throwable]

  val timer = ExecutorStub.newSequentialExecutor
  val executor = timer

  def handleUncaughtException (e: Throwable)  =  exception = Some (e)

  def makeThump (s: Scheduler, k: () => Any) =
    SchedulerConfig.makeSafeThump (s, k)

  def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
    SchedulerConfig.makeSafeThunk (s, k)

  def run (cond: => Boolean) {
    while (exception == None && !executor.isQuiet)
      executor.executeOne()
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

  val timer = ExecutorStub.newRandomExecutor (random)
  val executor = timer

  def handleUncaughtException (e: Throwable)  =  exception = Some (e)

  def makeThump (s: Scheduler, k: () => Any) =
    SchedulerConfig.makeSafeThump (s, k)

  def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) =
    SchedulerConfig.makeSafeThunk (s, k)

  def run (cond: => Boolean) {
    while (exception == None && !executor.isQuiet)
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

  def shutdown() {
    executor.shutdownNow()
    timer.shutdownNow()
  }}

object TestScheduler {

  def sequential (): TestScheduler =
    new TestScheduler (new SequentialConfig)

  def random (seed: Long): TestScheduler =
    new TestScheduler (new RandomConfig (new Random (seed)))

  def random (random: Random = Random): TestScheduler =
    new TestScheduler (new RandomConfig (random))

  /** By default, run until the condition is false, that is use `cond` for `run()`. */
  def multithreaded (cond: => Boolean): TestScheduler =
    new TestScheduler (new MultithreadedConfig (cond))
}
