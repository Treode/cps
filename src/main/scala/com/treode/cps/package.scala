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

package com.treode

import scala.util.continuations.{cpsParam, shift => _shift, shiftUnit}

/** Tools to write software that runs in a non-blocking manner by using Scala's continuations.
  *
  * A common technique for asynchronous I/O involves passing a completion handler (or callback) to
  * methods such as read and write.  The asynchronous version of such a method returns immediately,
  * and invokes the completion handler sometime later after the operation has finished.  Engineers
  * often use these techniques in servers and other systems that perform a great deal of I/O, since
  * they can yield better performance.
  *
  * Writing large programs in this style can produce somewhat obfuscated code, whereas Scala's
  * continuations plugin can automatically transform more naturally written code into such a style,
  * known as Continuation Passing Style (CPS).  This package wraps the Java asynchronous IO
  * operations with CPS type annotations (`@thunk`) and CPS primitives (`shift` and `reset`).
  * This package also provides some traditional synchronization mechanisms, like locks and futures,
  * that use CPS rather than thread blocking.
  */
package object cps {
  import CpsConversions._

  type thunk = cpsParam [Unit, Unit]

 /** Bring Unit into the `@thunk` tag.  This is useful when the continuations plugin complains
   * that every clause of an conditional or pattern matching expression must be `@thunk`.  This is
   * similar to `cede` but does not yield to the scheduler.
   */
  def cut (): Unit @thunk = shiftUnit [Unit, Unit, Unit] (())

  /** Bring a value into the `@thunk` tag.  This is useful when the continuations plugin complains
    * that every clause of an conditional or pattern matching expression must be `@thunk`. This is
    * similar to `cede` but does not yield to the scheduler.
    *
    * @param v The value to lift.
    * @return The value annotated `@thunk`.
    */
  def cut [A] (v: A): A @thunk = shiftUnit [A, Unit, Unit] (v)

  /** The shift used throughout this CPS package, which is more limited than the fully generic
    * shift from Scala's continuations.
    */
  def shift [A] (f: (A => Unit) => Any): A @thunk = _shift [A, Unit, Unit] (k => f (k))

  def whilst [A] (cond: => Boolean) (f: => A @thunk): CpsMonad [A] =
    new Repeater (cond, f)

  implicit def cpsIterableWrapper [A] (xs: Iterable [A]): CpsIterableWrapper [A] =
    new CpsIterableWrapper (xs)

  implicit def cpsOptionWrapper [A] (x: Option [A]): CpsOptionWrapper [A] =
    new CpsOptionWrapper (x)
}
