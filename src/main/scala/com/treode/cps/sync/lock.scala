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

import scala.util.control.ControlThrowable
import com.treode.cps.scheduler.Scheduler

/** A mutual exclusion lock, which allows only one acquisition at a time. */
trait Lock {

  /** Perform the computation while holding this lock, ensuring no other computation holds this lock
   * simultaneously.  Acquires the lock, waiting for it to be released if necessary, then performs
   * the computation, and finally releases the lock.
   */
  def exclusive [T] (f: => T @thunk): T @thunk
}

private class LockImpl (protected [this] val scheduler: Scheduler)
extends AtomicState with Lock {
  import scheduler.suspend

  initialize (Unlocked)

  protected [this] trait State {

    def acquire (k: Unit => Unit): Option [Unit]

    def release (): Option [Unit]

    def reverse (out: List [Unit => Unit]): Option [Unit] =
      throw new IllegalStateException ("Cannot reverse when not reversing.")
  }

  /** No fiber holds the lock, and no fiber is waiting on the lock. */
  private [this] object Unlocked extends State {

    // Let the caller proceed immediately.
    def acquire (k: Unit => Unit) = move (this, Locked) (k ())

    def release () = throw new IllegalStateException ("Cannot release unacquired lock.")
  }

  /** A fiber holds the lock, and no fiber is waiting. */
  private [this] object Locked extends State {

    // Queue the caller.
    def acquire (k: Unit => Unit) = move (this, new Queued (k)) (())

    def release () = move (this, Unlocked) (())
  }

  /** A fiber holds the lock, and some fibers are waiting. */
  private [this] class Queued (in: List [Unit => Unit], out: List [Unit => Unit]) extends State {
    def this (k: Unit => Unit) = this (List (), List (k))

    // Queue the caller.
    def acquire (k: Unit => Unit) = move (this, new Queued (k::in, out)) (())

    def release () = (in, out) match {
      // When in state Queued, in or out should have waiters.
      case (List (), List ()) => throw new AssertionError ("Queue should not be empty.")

      // Handoff to the last waiter.
      case (List (), List (k)) => move (this, Locked) (k ())

      // Handoff to the next waiter.
      case (_, k::ks) => move (this, new Queued (in, ks)) (k ())

      // Reverse the `in` queue of waiters.
      case (_, List ()) => move (this, new Reversing ()) {
        LockImpl.this.reverse (in.reverse)
      }}}

  /** Some fiber is reversing the `in` list; queue waiters. */
  private [this] class Reversing private (in: List [Unit => Unit]) extends State {
    def this () = this (List ())

    // Queuer the waiter.
    def acquire (k: Unit => Unit) = move (this, new Reversing (k :: in)) (())

    // No fiber should hold the lock while we reverse the `in` list.
    def release () = throw new IllegalStateException ("Cannot release unacquired lock.")

    // The fiber reversing the `in` list has finished, and presents it as the new `out` list.
    override def reverse (out: List [Unit => Unit]) =
      out match {
        // When in state Reversing, the `in` list being reversed should have had waiters.
        case List () => throw new AssertionError ("Out queue should not be empty.")

        // Handoff to the last waiter.
        case List (k) => move (this, Locked) (k ())

        // Handoff to the next waiter.
        case k::ks => move (this, new Queued (in, ks)) (k ())
      }}

  private [this] def acquire () =
    suspend [Unit] (k => delegate (_.acquire (k)))

  private [this] def release () = delegate (_.release ())

  private [this] def reverse (out: List [Unit => Unit]) = delegate (_.reverse (out))

  def exclusive [T] (f: => T @thunk): T @thunk = {
    acquire ()
    try {
      val v = f
      release ()
      v
    } catch {
      case e: ControlThrowable => throw e
      case e: Throwable => release (); throw e
    }}}

object Lock {

  def apply () (implicit s: Scheduler): Lock = new LockImpl (s)
}
