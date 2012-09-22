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

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.treode.cps.scheduler.Scheduler

/** Compose the [[http://en.wikipedia.org/wiki/State_pattern state pattern]] with
  * [[http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/atomic/AtomicReference.html AtomicReference]]
  * in a kind of poor man's
  * [[http://en.wikipedia.org/wiki/Optimistic_concurrency_control optimistic concurrency]].  An
  * operation eagerly forms the next state, in a manner free of side effects, and then attempts to
  * set the next state.  If it succeeds then the operation performs any side effects, otherwise the
  * operation delegates itself to the next state.  Objects which use the state pattern internally
  * and operate in a multithreaded environment can extend this class to manage state transitions
  * safely.
  */
trait AtomicState {

  protected [this] val scheduler: Scheduler

  /** The supertype of all states this object may move through.  Define this to be a trait that
    * lists the actions that one may perform on the object.
    */
  protected [this] type State

  private [sync] val state: AtomicReference [State] = new AtomicReference

  // _Obviously_ one would introduce an abstract def to get the initial state, but then that
  // definition references any inputs for computing the initial state, and thereby prevents their
  // garbage collection. Sadly, this was the cleanest way I could imagine that avoided leaking
  // memory.
  /** Invoke this during object creation to set the initial state; failure to do so will cause NPE
   * later.
   */
  protected [this] final def initialize (s: State) = state.set (s)

  /** Encapsulates the state transition and side effects of an operation. */
  protected [this] trait Behavior [A] {
    private [AtomicState] def execute (current: State): Option [A]
  }

  /** Encapsulates the state transition and side effects of an operation that is suspendable. */
  protected [this] trait SuspendableBehavior [A] {
    private [AtomicState] def execute (current: State, k: Thunk [A]): Boolean
  }

  /** Intermediate stage in specifying a behavior. */
  private [sync] final class Move (next: State) {

    /** If the move to the next state succeeds, perform no further action. */
    def withoutEffect = new Behavior [Unit] {
      private [AtomicState] def execute (current: State): Option [Unit] = {
        if (state.compareAndSet (current, next)) Some (Unit) else None
      }}

    /** If the move to the next state succeeds, perform this action and return the result to the
      * caller.  Another thread may have already changed the state since the move which yielded this
      * effect.  You must take care to not depend on the current state being that state which led to
      * this move nor the state this move set.
      *
      * @param action The action to perform.  @return The result of the action.
      */
    def withEffect [A] (action: => A) = new Behavior [A] {
      private [AtomicState] def execute (current: State): Option [A] = {
        if (state.compareAndSet (current, next)) Some (action) else None
      }}

    /** If the move to the next state succeeds, perform this suspendable action and return the
      * result to the caller.  Another thread may have already changed the state since the move
      * which yielded this effect.  You must take care to not depend on the current state being
      * that state which led to this move nor the state this move set.
      *
      * @param action The action to perform.  @return The result of the action.
      */
    def withEffectS [A] (action: => A @thunk) = new SuspendableBehavior [A] {
      private [AtomicState] def execute (current: State, k: Thunk [A]): Boolean = {
        val r = state.compareAndSet (current, next)
        if (r) scheduler.spawn (k (action))
        r
      }}}

  /** Move to the given next state.  A move may fail if another thread changed the state already
    * while this one formed this next state, in which case this next state will be discarded, and
    * the operation will be retried in the new current state.  You must take care to not create any
    * side effects assuming the move to this state was successful; instead follow this `withEffect`
    * which will only be invoked if the move succeeded.
    *
    * @param next The state to change to.
    */
  protected [this] final def moveTo (next: State) = new Move (next)

  /** Perform an action and return the result to the caller, without moving to a new state.
    *
    * @param action The action to perform.
    * @return The result of the action.
    */
  protected [this] final def effect [A] (action: => A) = new Behavior [A] {
    private [AtomicState] def execute (current: State): Option [A] = {
      Some (action)
    }}

  /** Perform a suspendable action and return the result to the caller, without moving to a new
     * state.
    *
    * @param action The action to perform.
    * @return The result of the action.
    */
  protected [this] final def effectS [A] (action: => A @thunk) = new SuspendableBehavior [A] {
    private [AtomicState] def execute (current: State, k: Thunk [A]): Boolean = {
      k.flowS (action)
      true
    }}

  /** Throw an exception.  Use this rather than `throw` directly, since the underlying
    * implementation may do funky CPS tricks that could cause a `throw` to be caught in an
    * unexpected place.
    *
    * @param e The exception to throw.
    */
  protected [this] final def toss [A] (e: => Throwable) = new Behavior [A] {
    private [AtomicState] def execute (current: State): Option [A] = {
      throw e
    }}

  /** Throw an exception.  Use this rather than `throw` directly, since the underlying
     * implementation may do funky CPS tricks that could cause a `throw` to be caught in an
     * unexpected place.
     *
     * @param e The exception to throw.
     */
  protected [this] final def tossS [A] (e: => Throwable) = new SuspendableBehavior [A] {
    private [AtomicState] def execute (current: State, k: Thunk [A]): Boolean = {
      k.fail (e)
      true
    }}

  /** Block the attempt at the action by throwing an IllegalStateException.  Use this when the
    * action is not permitted in the current state.
    *
    * @param message The message to include in the exception.
    */
  protected [this] final def illegalState [A] (message: String) =
    toss [A] (new IllegalStateException (message))

  /** There is an internal error; throw an AssertionError.
    *
    * @param message The message to include in the exception.
    */
  protected [this] final def assertionError [A] (message: String) =
    toss [A] (new AssertionError (message))

  /** Delegate an action to the current state.
    *
    * @param method A function to fetch the move from the delegate.
    */
  @tailrec
  protected [this] final def delegate [A] (method: State => Behavior [A]): A = {
    val current = state.get
    // getOrElse hides tailcall from compiler
    method (current) execute (current) match {
      case Some (v) => v
      case None => delegate (method)
    }}

  @tailrec
  private def delegateS [A] (method: State => SuspendableBehavior [A], k: Thunk [A]): Unit = {
    val current = state.get
    if (!(method (current) execute (current, k))) delegateS (method, k)
  }

  /** Delegate a suspendable action to the current state.
    *
    * @param method A function to fetch the move from the delegate.
    */
  protected [this] final def delegateS [A] (method: State => SuspendableBehavior [A]): A @thunk =
    scheduler.suspend [A] (k => delegateS (method, k))

  /** Capture the current continuation and then delegate an action to the current state.
    *
    * @param method A function to fetch the move from the delegate.
    */
  protected [this] final def delegateT [A] (method: State => Thunk [A] => Behavior [Unit]): A @thunk =
    scheduler.suspend [A] (k => delegate (method (_) (k)))

  /** Delegate to a reader of the current state.  The delegate method cannot change the state; it
    * can only return a value computed from the current state.
    */
  protected [this] final def fetch [A] (method: State => A): A =
    method (state.get)

  override def toString = state.get.toString
}
