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
package sync

import scala.util.continuations.reset
import scala.util.control.ControlThrowable
import com.treode.cps.scheduler.Scheduler

trait Future [+A] {

  def get: A @thunk

  def filter (p: A => Boolean): Future [A] =
    map (v => if (p (v)) v else throw new NoSuchElementException)

  def map [B] (f: A => B): Future [B]
  def flatMap [B] (f: A => Future [B]): Future [B]
  def foreach (f: A => Unit): Unit
}

/** The computation has not run. */
private class Promise [A] (
  protected [this] implicit val scheduler: Scheduler
) extends Future [A] with AtomicState {
  import scheduler.suspend

  protected [this] trait State {

    def get (k: Thunk [A]): Option [Unit]
    def set (v: A): Option [Unit]
    def fail (e: Throwable): Option [Unit]
  }

  // The computation has not started yet.
  private [sync] class Delayed (f: => A @thunk) extends State {

    private def block () = throw new IllegalStateException ("The future is delayed.")

    // Start the computation and queue this continuation.
    def get (k: Thunk [A]) = move (this, new Running (k)) (run (f))

    // The computation has not started, so it cannot have completed.
    def set (v: A) = block ()

    def fail (e: Throwable) = block ()
  }

  // The computation has started but not finished.
  private [sync] class Running (q: List [Thunk [A]]) extends State {
    def this () = this (List ())
    def this (k: Thunk [A]) = this (List (k))
    def this (k: Thunk [A], q: List [Thunk [A]]) = this (k :: q)

    // Queue this continuation.
    def get (k: Thunk [A]) = move (this, new Running (k, q)) (())

    // Store the result, resume the queued continuations.
    def set (v: A) = move (this, new Ready (v)) (for (k <- q) (k (v)))

    def fail (e: Throwable) = move (this, new Failed (e)) (for (k <- q) (k.fail (e)))
  }

  // The computation has finished.
  private class Ready (v: A) extends State {

    private def block () = throw new IllegalStateException ("The future is already set.")

    // No need to wait; resume the continuation now.
    def get (k: Thunk [A]) = effect (k (v))

    // The computation already completed; we cannot complete it again.
    def set (v: A) = block ()

    def fail (e: Throwable) = block ()
  }

  // The computation has thrown an exception
  private class Failed (e: Throwable) extends State {

    private def block () = throw new IllegalStateException ("The future is already set.")

    // No need to wait; resume the continuation now.
    def get (k: Thunk [A]) = effect (k.fail (e))

    // The computation already completed; we cannot complete it again.
    def set (v: A) = block ()

    def fail (e: Throwable) = block ()
  }

  private [sync] def run (f: => A @thunk) {
    scheduler.spawn {
      reset [Unit, Unit] {
        try {
          Promise.this.set (f)
        } catch {
          case e: ControlThrowable => throw e
          case e: Throwable => fail (e)
        }}}}

  private def set (v: A): Unit = delegate (_.set (v))

  private def fail (e: Throwable): Unit = delegate (_.fail (e))

  /** The result of this computation.  This will suspend until the computation is complete; it will
    * throw the exception if the computation did, and rethrow it if called multiple times.  An
    * exception thrown by the computation will not be seen, not even by `handleUncaughtException`,
    * unless get is called.  To start a computation and ignore its result but allow exceptions to be
    * be seen by `handleUncaughException`, use `Scheduler.spawn`.
    */
  def get: A @thunk = suspend [A] (k => delegate (_.get (k)))

  def map [B] (f: A => B): Future [B] = Future.start (f (get))
  def flatMap [B] (f: A => Future [B]) = Future.start (f (get).get)
  def foreach (f: A => Unit): Unit = scheduler.spawn (f (get))
}

/** The computation ran before constructing the future. */
private class Delivered [A] (v: A) extends Future [A] {

  def get: A @thunk = cut (v)

  def map [B] (f: A => B): Future [B] = Future.complete (f (v))
  def flatMap [B] (f: A => Future [B]) = f (v)
  def foreach (f: A => Unit): Unit = f (v)
}

object Future {

  /** Disguise a value as a Future using a thin wrapper that avoids synchronization overhead, also
    * known as an immediate future.  If one can use `map` and `flatMap` to process the result, then
    * one can ensure the resulting future is immediate if all the future dependencies are immediate.
    * For example, code like:
    * {{{
    * start {
    *     val x: Future [Int] = produceX
    *     funcX (x.get)
    * }
    * }}}
    * will always introduce a future with synchronization overhead, even though the result of x
    * might be an immediate future.  Instead, the above can be written:
    * {{{
    * produceX .map (funcX (_))
    * }}}
    * and then it will yield an immediate future if `produceX` yielded an immediate future.
    *
    * @param v The result of a computation already performed.
    */
  def complete [A] (v: A): Future [A] = new Delivered (v)

  /** Await the value of each computation, avoiding synchronization overhead if every value of the
    * iterable is an immediate future.
    *
    * @param vs The results to await.
    */
  def join [A] (vs: Iterable [Future [A]]): Future [List [A]] = {
    // By folding over flatMap rather than mapping over _.get, the resulting future will be
    // immediate if every future of the iterable is immediate.
    val zero = Future.complete (List [A] ())
    def succ (n: Future [List [A]], f: Future [A]) = f .flatMap (v => n .map (vs => v :: vs))
    vs .foldLeft (zero) (succ _) .map (_.reverse)
  }

  /** Start a computation immediately, and return a Future to obtain its result later.  This may
    * return to the caller before the computation has completed.
    *
    * @param f The computation to perform.
    * @param s The scheduler to suspend fibers waiting on the value.
    */
  def start [A] (f: => A @thunk) (implicit s: Scheduler): Future [A] =
    new Promise [A] {
      initialize (new Running)
      run (f)
    }

  /** Delay a computation until its result is needed, and return a Future to obtain its result
    * later.
    *
    * @param f The computation to perform.
    * @param s The scheduler to suspend fibers waiting on the value.
    */
  def delay [A] (f: => A @thunk) (implicit s: Scheduler): Future [A] =
    new Promise [A] {
      initialize (new Delayed (f))
    }}
