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

  initialize (Unlocked)

  protected [this] trait State {

    def acquire () (k: Unit => Unit): Behavior [Unit]

    def release (): Behavior [Unit]

    def reverse (out: List [Unit => Unit]): Behavior [Unit] =
      illegalState ("Cannot reverse when not reversing.")
  }

  /** No fiber holds the lock, and no fiber is waiting on the lock. */
  private [this] object Unlocked extends State {

    // Let the caller proceed immediately.
    def acquire () (k: Unit => Unit) = moveTo (Locked) withEffect (k ())

    def release () = illegalState ("Cannot release unacquired lock.")
  }

  /** A fiber holds the lock, and no fiber is waiting. */
  private [this] object Locked extends State {

    // Queue the caller.
    def acquire () (k: Unit => Unit) = moveTo (new Queued (k)) withoutEffect

    def release () = moveTo (Unlocked) withoutEffect
  }

  /** A fiber holds the lock, and some fibers are waiting. */
  private [this] class Queued (in: List [Unit => Unit], out: List [Unit => Unit]) extends State {
    def this (k: Unit => Unit) = this (List (), List (k))

    // Queue the caller.
    def acquire () (k: Unit => Unit) = moveTo (new Queued (k::in, out)) withoutEffect

    def release () = (in, out) match {
      // When in state Queued, in or out should have waiters.
      case (List (), List ()) => assertionError [Unit] ("Queue should not be empty.")

      // Handoff to the last waiter.
      case (List (), List (k)) => moveTo (Locked) withEffect (k ())

      // Handoff to the next waiter.
      case (_, k::ks) => moveTo (new Queued (in, ks)) withEffect (k ())

      // Reverse the `in` queue of waiters.
      case (_, List ()) => moveTo (new Reversing ()) withEffect {
        LockImpl.this.reverse (in.reverse)
      }}}

  /** Some fiber is reversing the `in` list; queue waiters. */
  private [this] class Reversing private (in: List [Unit => Unit]) extends State {
    def this () = this (List ())

    // Queuer the waiter.
    def acquire () (k: Unit => Unit) = moveTo (new Reversing (k :: in)) withoutEffect

    // No fiber should hold the lock while we reverse the `in` list.
    def release () = illegalState ("Cannot release unacquired lock.")

    // The fiber reversing the `in` list has finished, and presents it as the new `out` list.
    override def reverse (out: List [Unit => Unit]) =
      out match {
        // When in state Reversing, the `in` list being reversed should have had waiters.
        case List () => assertionError [Unit] ("Out queue should not be empty.")

        // Handoff to the last waiter.
        case List (k) => moveTo (Locked) withEffect (k ())

        // Handoff to the next waiter.
        case k::ks => moveTo (new Queued (in, ks)) withEffect (k ())
      }}

  private [this] def acquire () = delegateT (_.acquire ())

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
