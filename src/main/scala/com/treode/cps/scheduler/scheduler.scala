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
package scheduler

import java.util.concurrent.{Executor, ScheduledExecutorService, TimeUnit}
import scala.util.control.ControlThrowable
import com.treode.cps.sync.AtomicState

import TimeUnit.MILLISECONDS

private class SafeThump (val scheduler: Scheduler, k: () => Any)
extends AtomicState with Runnable {

  initialize (Suspended)

  protected trait State {
    def run (): Option [Unit]
  }

  def run (): Unit = delegate (_.run ())

  private def fail (m: String) =
    scheduler.handleUncaughtException (new IllegalStateException (m))

  private object Suspended extends State {
    def run () = move (this, Executed) (execute (scheduler, k))
  }

  private object Executed extends State {
    def run () = move (this, Executed) (fail ("Thump already executed"))
  }}

private class FastThump (s: Scheduler, k: () => Any) extends Runnable {

  def run (): Unit = execute (s, k)
}

private class SafeThunk [A] (val scheduler: Scheduler, k: Either [Throwable, A] => Any)
extends AtomicState with Thunk [A] {
  import scheduler.spawn

  initialize (Suspended)

  protected [this] trait State {
    def resume (v: A): Option [Unit]
    def fail (e: Throwable): Option [Unit]
    def flow (v: => A): Option [Unit]
    def flowS (v: => A @thunk): Option [Unit]
  }

  private [this] object Suspended extends State {

    def resume (v: A) =
      move (this, Resumed) (spawn (k (Right (v))))

    def fail (e: Throwable) =
      move (this, Resumed) (spawn (k (Left (e))))

    def flow (v: => A) =
      move (this, Resumed) (catcher (scheduler, k, v))

    def flowS (v: => A @thunk) =
      move (this, Resumed) (catcherS (scheduler, k, v))
  }

  private [this] object Resumed extends State {

    private [this] def alreadyResumed: Option [Unit] =
      throw new IllegalStateException ("Continuation already resumed.")

    def resume (v: A) = alreadyResumed
    def fail (e: Throwable) = alreadyResumed
    def flow (v: => A) = alreadyResumed
    def flowS (v: => A @thunk) = alreadyResumed
  }

  def apply (v: A): Unit = delegate (_.resume (v))
  def fail (e: Throwable): Unit = delegate (_.fail (e))
  def flow (v: => A): Unit = delegate (_.flow (v))
  def flowS (v: => A @thunk): Unit = delegate (_.flowS (v))
}

private class FastThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any) extends Thunk [A] {

  def apply (v: A): Unit = s.spawn (k (Right (v)))
  def fail (e: Throwable): Unit = s.spawn (k (Left (e)))
  def flow (v: => A): Unit = catcher (s, k, v)
  def flowS (v: => A @thunk): Unit = catcherS (s, k, v)
}

private class RunThunk [A] (s: Scheduler, k: => A @thunk) extends Runnable {

  def run() = s.spawn (k)
}

// These are here rather than as constructor arguments to Scheduler primarily to allow the type
// argument on makeThunk.  The companion object also provides a handy place to gather the various
// values and methods a user may want to use for configuring the scheduler.
trait SchedulerConfig {

  /** The executor that decides when to run a task.  Invoking `apply` or `fail` on a
    * [[com.treode.cps.Thunk]] enqueues a task with this executor.
    */
  val executor: Executor

  /** The executor that runs tasks triggered after the passing of some time.  Tasks given to
   * `schedule` are passed through to this executor; we separated it from the main executor because
    * `ForkJoinPool` does not implement the interface.
    */
  val timer: ScheduledExecutorService

  /** Exceptions are passed up through a call chain of suspendable methods as one might assume,
    * but they are not passed through [[com.treode.cps.scheduler.Scheduler#spawn]].  Exceptions that
    * would otherwise unwind the call stack through `spawn` are instead caught and handed to this
    * method.
    */
  def handleUncaughtException (e: Throwable): Unit

  /** A thump is the runnable that we hand to the [[com.treode.cps.scheduler.TaskQueue]].  A
    * [[com.treode.cps.scheduler.SchedulerConfig#makeSafeThump]] ensures that the task runs at most
    * once, and may help finding problems in a TaskQueue implementation, however it incurs
    * synchronization overhead.  A
    * [[com.treode.cps.scheduler.SchedulerConfig#makeFastThump]] avoids that cost but does not
    * detect multiple runs.
    */
  def makeThump (s: Scheduler, k: () => Any): Runnable

  /** A thunk captures the continuation, allowing a user to resume the continuation later with a
    * returned value or with an thrown exception.  A thunk functions much like the callbacks common
    * in asynchronous programming, but syntactically it looks like a traditional method invocation.
    * In fact, the CPS plugin works by rewriting the code from a traditional style to a callback
    * style.
    *
    * A [[com.treode.cps.scheduler.SchedulerConfig#makeSafeThunk]] ensures that the continuation
    * is resumed (either with a value or with an exception) at most once, and it may help finding
    * bugs in your code, however it incurs synchronization overhead.  A
    * [[com.treode.cps.scheduler.SchedulerConfig#makeFastThunk]] avoids that cost but does not
    * detect multiple resumptions.
    */
  def makeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any): Thunk [A]
}

object SchedulerConfig {

  def makeFastThump (s: Scheduler, k: () => Any): Runnable =
    new FastThump (s, k)

  def makeSafeThump (s: Scheduler, k: () => Any): Runnable =
    new SafeThump (s, k)

  def makeFastThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any): Thunk [A] =
    new FastThunk (s, k)

  def makeSafeThunk [A] (s: Scheduler, k: Either [Throwable, A] => Any): Thunk [A] =
    new SafeThunk (s, k)
}

class Scheduler private [cps] (cfg: SchedulerConfig) {
  import cfg.{executor, makeThump, makeThunk, timer}

  private def reset (k: => Any @thunk): Unit =
    scala.util.continuations.reset { k; () }

  /** @see [[com.treode.cps.scheduler.SchedulerConfig#handleUncaughtException]] */
  // A user implementing his/her own thump will need access to this.
  def handleUncaughtException (e: Throwable) =
    cfg.handleUncaughtException (e)

  /** Spawn a task to perform as soon as possible.  This may return to the caller immediately
    * before the task has completed.  Conceptually this method is like creating a new thread,
    * however the underlying mechanism is much lighter weight.  To avoid confusion in this
    * documentation, we refer to the operation as "spawning a fiber."
    *
    * @param k The task to perform.
    */
  def spawn [A] (k: => A @thunk): Unit =
    executor.execute (makeThump (this, () => reset (k)))

  // This reduces risk of bleeding eyes in the definition of suspend.
  private def catcher [A] (f1: Thunk [A] => Any) (f2: Either [Throwable, A] => Any): Unit =
    try {
      f1 (makeThunk (this, f2))
    } catch {
      case e: ControlThrowable => throw e
      case e => f2 (Left (e))
    }

  // This helps the type inferencing along.
  private def unwrap [A] (x: Either [Throwable, A]): A =
    if (x.isRight) x.right.get else throw x.left.get

   /** Suspend the current computation and capture the continuation.
     *
     * @param f The capturing function that will invoke the continuation when data is ready.
     * @return The value provided by the capturing function.
     */
  def suspend [A] (f: Thunk [A] => Any): A @thunk =
    unwrap (shift (catcher (f) _))

  /** Pause this fiber and yield processing resources to another fiber.  This is the CPS analog to
    * `Thread.yield`, but `cede` is not a keyword of Scala.
    */
  def cede (): Unit @thunk = suspend [Unit] (_ ())

  /** Pause this fiber and yield processing resources to another fiber, and also lift the value
    * into the `@thunk` tag.  This is the CPS analog to `Thread.yield`, but `cede` is not a keyword
    * of Scala.
    *
    * @param v The value to lift.
    * @return The value annotated `@thunk`
    */
  def cede [A] (v: A): A @thunk = suspend [A] (_ (v))

  /** Perform the task after a delay. */
  def schedule [A] (delay: Int, unit: TimeUnit = MILLISECONDS) (k: => A @thunk): Unit =
    timer.schedule (new RunThunk (this, k), delay, unit)

  def sleep (delay: Int, unit: TimeUnit = MILLISECONDS): Unit @thunk =
    shift [Unit] (k => schedule (delay, unit) (k()))

  /** Pause the calling thread until completion of `k`.  To pass work from a traditional threaded
    * system into a CPS system, directly invoke `spawn`, `Future.start`, `Future.delay` or
    * `Mailbox.send`. To retrieve a value from the CPS system into a traditional threaded system,
    * use this method.
    */
  def await [T] (k: => T @thunk): T = {
      val q = new java.util.concurrent.SynchronousQueue [Either [Throwable, T]]
      spawn (q.put (catcherK (k)))
      q.take() match {
        case Right (v) => v
        case Left (e) => throw e
      }}}

object Scheduler {

  def apply (config: SchedulerConfig) = new Scheduler (config)
}
