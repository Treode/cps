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

package com.treode.cps.stub.scheduler

import java.util.{Collection => JCollection, List => JList}
import java.util.concurrent.{Future => JFuture, Callable, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.collection.mutable
import scala.util.Random

private case class ScheduledTask (trigger: Long, r: Runnable)

private trait ExecutorStub extends ScheduledExecutorService {

  /** False if the subclass has no tasks to execute _immediately_. */
  protected def isQuietNow: Boolean

  /** Perform one _immediate_ task; the subclass may assume isQuietNow is false. */
  protected def executeOneNow(): Unit

  // Order scheduled tasks by the trigger time.
  private [this] val order = new Ordering [ScheduledTask] {
    def compare (x: ScheduledTask, y: ScheduledTask): Int = x.trigger compare y.trigger
  }

  // Our tasks scheduled for some future time; the subclass manages only immediate tasks, and
  // this superclass handles moving scheduled (time delayed) tasks into the immediate queue after
  // the desired delay.
  private [this] val scheduled =
      new mutable.SynchronizedPriorityQueue [ScheduledTask] () (order)

  // If the user requests a delay of two hours, but there are not two hours worth of immediate
  // activities, we jump in time to trigger the delayed tasks sooner.  This tracks the running
  // total of jumps, which is then added to new tasks.
  private [this] var time = 0L

  /** False until there are no more tasks enqueued. */
  def isQuiet (timers: Boolean): Boolean = isQuietNow && (!timers || scheduled.isEmpty)

  /** Perform one task; isQuiet must be false. */
  def executeOne (timers: Boolean) {
    if (timers && (scheduled.headOption exists (_.trigger < time))) {
      // A timer has triggered, move its task to the immediate queue.
      time += 1
      execute (scheduled.dequeue.r)
    } else if (isQuietNow) {
      // There's no immediate task, jump time to that of the next scheduled task.
      val t = scheduled.dequeue
      time = t.trigger + 1
      execute (t.r)
    } else {
      // Execute the next immediate task.
      time += 1
      executeOneNow()
    }}

  def schedule (r: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture [_] = {
    val msec = TimeUnit.MILLISECONDS.convert (delay, unit)
    scheduled.enqueue (ScheduledTask (time + msec, r))
    // The stub kits ignore the result.
    null .asInstanceOf [ScheduledFuture [_]]
  }

  // The Java interfaces require us to make up stuff.
  private def unsupported [A] = throw new UnsupportedOperationException

  // The stub kits do not use any of these, but the Java interface for ExecutorService demands
  // their implementations.
  def awaitTermination (timeout: Long, unit: TimeUnit): Boolean = unsupported
  def invokeAll [A] (tasks: JCollection [_ <: Callable [A]]): JList [JFuture [A]] = unsupported
  def invokeAll [A] (tasks: JCollection [_ <: Callable [A]], timeout: Long, unit: TimeUnit): JList [JFuture [A]] = unsupported
  def invokeAny [A] (tasks: JCollection [_ <: Callable [A]]): A = unsupported
  def invokeAny [A] (tasks: JCollection [_ <: Callable [A]], timeout: Long, unit: TimeUnit): A = unsupported
  def isShutdown(): Boolean = unsupported
  def isTerminated(): Boolean = unsupported
  def shutdown(): Unit = unsupported
  def shutdownNow(): JList [Runnable] = unsupported
  def submit [A] (task: Callable [A]): JFuture [A] = unsupported
  def submit (task: Runnable): JFuture [_] = unsupported
  def submit [A] (task: Runnable, result: A): JFuture [A] = unsupported

  // The stub kits do not use any of these either , but the Java interface for
  // ScheduledExecutorService demands their implementations.
  def schedule [A] (callable: Callable [A], delay: Long, unit: TimeUnit): ScheduledFuture [A] = unsupported
  def scheduleAtFixedRate (command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture [_] = unsupported
  def scheduleWithFixedDelay (command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture [_] = unsupported
}

/** An executor that chooses the next enqueued tasks and performs it. */
private class SequentialStub extends ExecutorStub {

  private [this] val queue = mutable.Queue [Runnable] ()

  def execute (r: Runnable) = queue.enqueue (r)

  protected def isQuietNow: Boolean = queue.isEmpty

  protected def executeOneNow() = queue.dequeue.run()
}

/** An executor that randomly chooses one enqueued task and performs it. */
private class RandomStub (r: Random) extends ExecutorStub {

  private [this] val queue = ChoosyQueue [Runnable] ()

  def execute (r: Runnable): Unit = queue.enqueue (r)

  protected def isQuietNow: Boolean = queue.isEmpty

  protected def executeOneNow(): Unit = queue .dequeue (r) .run()
}
