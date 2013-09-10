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

package com.treode.cps.scalatest

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.continuations.reset
import org.scalacheck.{Gen, Prop, Pretty, Shrink}
import org.scalatest.{Assertions, FlatSpec, PropSpec}
import org.scalatest.prop.PropertyChecks
import org.scalatest.verb.{ResultOfStringPassedToVerb, ResultOfTaggedAsInvocation}

import com.treode.cps.{shift, thunk}
import com.treode.cps.stub.scheduler.TestScheduler

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

  def resetTest (k: => Any @thunk) {
    reset {
      k
      ()
    }}

  def wrapRun [A] (k: => A @thunk) (implicit scheduler: TestScheduler): A = {
    var finished = false
    var v = null .asInstanceOf [A]
    scheduler.spawn {
      v = k
      expectResult (false, "CPS test ran twice.") (finished)
      finished = true
    }
    scheduler.run (!finished)
    v
  }

  def withScheduler [A <: TestScheduler] (newScheduler: => A): A @thunk = {
    shift [A] { k =>
      implicit val scheduler = newScheduler
      try {
        wrapRun (k (scheduler))
        scheduler.shutdown()
      } catch {
        case exn =>
          scheduler.shutdown()
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

  protected final class OnForString (v: ResultOfStringPassedToVerb) {
    def on (ctx: => Any @thunk) =
      v in (resetTest (ctx))
  }

  implicit def onForString (v: ResultOfStringPassedToVerb): OnForString =
    new OnForString (v)

  protected final class OnForIt (v: ItVerbString) {
    def on (ctx: => Any @thunk) =
      v in (resetTest (ctx))
  }

  implicit def onForIt (v: ItVerbString): OnForIt =
    new OnForIt (v)

  protected final class OnForTagged (v: ResultOfTaggedAsInvocation) {
    def on (ctx: => Any @thunk) =
      v in (resetTest (ctx))
  }

  implicit def onForTagged (v: ResultOfTaggedAsInvocation): OnForTagged =
    new OnForTagged (v)

  protected final class OnForItTagged (v: ItVerbStringTaggedAs) {
    def on (ctx: => Any @thunk) =
      v in (resetTest (ctx))
  }

  implicit def onForItTagged (v: ItVerbStringTaggedAs): OnForItTagged =
    new OnForItTagged (v)
}

trait CpsPropSpec extends PropSpec with PropertyChecks with CpsSpecTools {

  val seeds = Gen.choose (0L, Long.MaxValue)

  def forAllS [A] (ga: Gen [A]) (f: A => Unit @thunk) (
      implicit cfg: PropertyCheckConfig, sa: Shrink [A]): Unit =
    forAll (ga) (a => resetTest (f (a))) (cfg, sa)
}
