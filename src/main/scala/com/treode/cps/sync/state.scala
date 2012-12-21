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
private [cps] trait AtomicState {

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

  /** If the move to the next state succeeds, perform the action and return its result.  Another
    * thread may have already changed the state since the move which yielded this effect.  You must
    * take care to not depend on the current state being that state which led to this move nor the
    * state this move set.
    *
    * @param now The current state, usually State.this
    * @param next The next state, setting the next state may fail.
    * @param action The action to perform if setting the next state passed.
    * @return The result of that action.
    */
  def move [A] (now: State, next: State) (action: => A): Option [A] =
    if (state.compareAndSet (now, next))
      Some (action)
    else
      None

  /** Yield a result based on the current state; this always succeeds. */
  def effect [A] (result: A): Option [A] = Some (result)

  /** Delegate an action to the current state.  If the method succeeds because no other thread
    * changed in the meantime (that is, the method returns Some), return that result. If the method
    * because some other thread did change the state (that is, the method returns None) then retry
    * the method on the new state.
    *
    * @param method A function to fetch the move from the delegate.
    */
  def delegate [A] (method: State => Option [A]): A = {
    var result = method (state.get)
    while (result.isEmpty)
      result = method (state.get)
    result.get
  }

  override def toString = state.get.toString
}
