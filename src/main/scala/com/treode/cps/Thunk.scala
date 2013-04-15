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

package com.treode.cps

/** Encapsulates a continuation.
  *
  * @tparam A The result type of the continuation.
  */
trait Thunk [A] extends (A => Unit) {

  /** Resume the continuation with the given value.
    *
    * @param v The value to provide to the continuation.
    * @throws IllegalStateException if this continuation was already resumed.
    */
  def apply (v: A): Unit

  /** Resume the continuation with the given exception.
    *
    * @param e The exception to throw in the continuation.
    * @throws IllegalStateException if this continuation was already resumed.
    */
  def fail (e: Throwable): Unit

  /** Resume the continuation with the given by-name value.  If the by-name argument throws an
    * exception, it will be passed to this thunk rather than the caller of `flow`.
    *
    * @param v The value to provide to the continuation.
    * @throws IllegalStateException if this continuation was already resumed.
    */
  def flow (v: => A): Unit

  /** Resume the continuation with the given suspendable by-name value.  If the by-name argument
    * throws an exception, it will be passed to this thunk rather than the caller of `flow`.
    *
    * @param v The value to provide to the continuation.
    * @throws IllegalStateException if this continuation was already resumed.
    */
  def flowS (v: => A @thunk): Unit
}
