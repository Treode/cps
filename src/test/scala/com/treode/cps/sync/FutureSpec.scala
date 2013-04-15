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

package com.treode.cps.sync

import org.scalatest.Specs
import org.scalatest.matchers.ShouldMatchers
import scala.collection.mutable
import com.treode.cps.scalatest.{CpsFlatSpec, CpsPropSpec}
import com.treode.cps.stub.scheduler.TestScheduler

import Future.{delay, start}

class FutureSpec extends Specs (FutureBehaviors, FutureProperties)

private object FutureBehaviors extends CpsFlatSpec {

  private [this] class DistinguishedException extends Exception
  private [this] def fatal: Unit = throw new DistinguishedException

  "A Future" should "pass an exception through" in {
    implicit val scheduler = TestScheduler.sequential ()
    import scheduler.spawn

    val x = start (fatal)
    spawn {
      interceptCps [DistinguishedException] (x.get)
    }
    scheduler.run ()
  }}

private object FutureProperties extends CpsPropSpec with ShouldMatchers {

  property ("An eager future runs every computation once immediately") {
    forAll (seeds) { seed: Long =>
      implicit val scheduler = TestScheduler.random (seed)
      import scheduler.{spawn, suspend}

      val log = mutable.Set [Int] ()
      val x1 = start { log.add (1) should be (true); 1 }
      val x2 = start { log.add (2) should be (true); 2 }
      val unused = start { log.add (3) should be (true); -1 }
      scheduler.run ()
      log should be (Set (1, 2, 3))

      val x3 = start {
        log.add (4) should be (true)
        val v1 = x1.get
        log.add (5) should be (true)
        val v2 = x2.get
        log.add (6) should be (true)
        (v1 + v2)
      }
      spawn {
        log.add (7) should be (true)
        val v3 = x3.get
        log.add (8) should be (true)
        v3 should be (3)
      }
      scheduler.run ()
      log should be ((1 to 8).toSet)
    }}

  property ("A lazy future runs only needed compuations once upon demand") {
    forAll (seeds) { seed: Long =>
      implicit val scheduler = TestScheduler.random (seed)
      import scheduler.spawn

      val log = mutable.Set [Int] ()
      val x1 = delay { log.add (1) should be (true); 1 }
      val x2 = delay { log.add (2) should be (true); 2 }
      val unused = delay { log.add (3) should be (true); -1 }
      scheduler.run ()
      log should be (Set [Int] ())
      spawn {
        val x3 = start {
          log.add (4) should be (true)
          val v1 = x1.get
          log.add (5) should be (true)
          val v2 = x2.get
          log.add (6) should be (true)
          (v1 + v2)
        }
        log.add (7) should be (true)
        val v3 = x3.get
        log.add (8) should be (true)
        v3 should be (3)
      }
      scheduler.run ()
      log should be (Set (1, 2, 4, 5, 6, 7, 8))
    }}}
