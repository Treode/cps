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

package com.treode.cps.scalatest

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.continuations.reset
import org.scalacheck.Gen
import org.scalatest.{Assertions, FlatSpec, PropSpec}
import org.scalatest.prop.PropertyChecks
import org.scalatest.verb.ResultOfStringPassedToVerb
import com.treode.cps.{shift, thunk}
import com.treode.cps.stub.CpsSpecKit

trait CpsSpecTools extends Assertions {

  type Random = scala.util.Random
  type Scheduler = com.treode.cps.scheduler.Scheduler

  /** Suppose that you have
    * <pre>
    * def compute: SomeObject @thunk
    * intercept [Exception] (compute)
    * </pre>
    * and that `compute` throws an exception, it will not be intercepted as expected.  Use this
    * `interceptCps` method to test for thrown exceptions from CPS methods.
    */
  def interceptCps [A] (f: => Any @thunk) (implicit m: Manifest [A]): A @thunk = {
    try {
      f
      expectResult (m.erasure.getCanonicalName) (None)
      null .asInstanceOf [A] // Never reached.
    } catch {
      case e =>
        if (! m.erasure.isInstance (e))
          expectResult (m.erasure.getCanonicalName) (e.getClass.getCanonicalName)
        e .asInstanceOf [A]
    }}

  def withWrappers (ctx: => Any @thunk): Unit = reset { ctx; () }

  def wrapRun [A] (kit: CpsSpecKit) (k: => A @thunk): A = {
    var finished = false
    var v = null .asInstanceOf [A]
    kit.scheduler.spawn {
      v = k
      expectResult (false, "CPS test ran twice.") (finished)
      finished = true
    }
    kit.run()
    expectResult (true, "CPS test did not complete.") (finished)
    v
  }

  def withCpsKit [A <: CpsSpecKit] (newKit: => A): A @thunk = {
    shift [A] { k =>
      val kit = newKit
      try {
        wrapRun (kit) (k (kit))
        kit.shutdown()
      } catch {
        case exn =>
          kit.shutdown()
          throw exn
      }}}

  def withLog (logs: String*): (String => Unit) @thunk =
    shift [String => Unit] { k =>
      val log = new ConcurrentHashMap [String, Boolean] ()
      val logger = {
        (s: String) =>
          expectResult (null, "Hit " + s + " a second time.") (log.put (s, true))
      }
      k (logger)
      expectResult (Set (logs: _*)) (log.keySet.toSet)
  }}

trait CpsFlatSpec extends FlatSpec with CpsSpecTools {

  protected final class DuringForString (v: ResultOfStringPassedToVerb) {
    def during (ctx: => Any @thunk) =
      v in {
        withWrappers {
          ctx
        }}}

  implicit def duringForString (v: ResultOfStringPassedToVerb): DuringForString =
    new DuringForString (v)

  protected final class DuringForIt (v: ItVerbString) {
    def during (ctx: => Any @thunk) =
      v in {
        withWrappers {
          ctx
        }}}

  implicit def duringForIt (v: ItVerbString): DuringForIt =
    new DuringForIt (v)
}

trait CpsPropSpec extends PropSpec with PropertyChecks with CpsSpecTools {

  val seeds = Gen.choose (0L, Long.MaxValue)
}
