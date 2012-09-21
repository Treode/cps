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

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import scala.collection.{generic, mutable}

import CpsConversions._

class CpsConversionsSpec extends FlatSpec with ShouldMatchers {

  // This requires care; see the "immediately" tests.
  private class Shifter [A] extends CpsMonad [A] {
    // k is a stub, until foreach is called
    private var k: A => Any = ((v: A) => ())
    // put will do nothing if it's called before foreach.
    def put (v: A) = k (v)
    // Call foreach first to set k, so put is feckful.
    def foreach (f: A => Any @thunk) = {
      val x = shift [A] ((_k: A => Any) => k = _k)
      f (x)
      ()
    }}

  "CpsConversions" should "support foreach" in {
    val kit = CpsSpecKit.newSequentialKit
    import kit.scheduler.spawn

    val q1 = mutable.Queue [Int] ()
    val q2 = mutable.Queue [Int] ()
    spawn {
      for (i <- (1 to 10).cps) (q1 += cut (i))
      (1 to 10).cps.foreach (q2 += cut (_))
    }
    kit.run ()
    val expected = mutable.Queue (1 to 10: _*)
    q1 should be (expected)
    q2 should be (expected)
  }

  it should "run foreach on each element immediately" in {
    val kit = CpsSpecKit.newSequentialKit
    import kit.scheduler.spawn

    val s = new Shifter [Int]
    var x = 0
    spawn {
      for (i <- s) (cut (x = i))
    }
    // Run the scheduler to set the continuation in the shifter.
    kit.run ()
    for (i <- 1 to 10) {
      s.put (i)
      // Run the scheduler to carry out as much as possible.
      kit.run ()
      // Check that work was done with awaiting the whole "collection."
      x should be (i)
    }}

  it should "support map" in {
    val kit = CpsSpecKit.newSequentialKit
    import kit.scheduler.spawn

    val expected = 2 to 20 by 2
    spawn {
      val s1 = {
        for (i <- (1 to 10).cps) yield (cut (i * 2))
      }.toList
      s1 should be (expected)
      val s2 = {
        (1 to 10).cps map ((i: Int) => cut (i * 2))
      }.toList
      s2 should be (expected)
    }
    kit.run ()
  }

  it should "run map on each element immediately" in {
    val kit = CpsSpecKit.newSequentialKit
    import kit.scheduler.spawn

    val s = new Shifter [Int]
    var x = 0
    spawn {
      for {
        i <- s
        val j = cut (i * 2)
      } (cut (x = j))
    }
    kit.run ()
    for (i <- 1 to 10) {
      s.put (i)
      kit.run ()
      x should be (i*2)
    }}

  it should "support flatMap" in {
    val kit = CpsSpecKit.newSequentialKit
    import kit.scheduler.spawn

    val expected = (1 to 10) flatMap (_ to 10)
    spawn {
      val s1 = {
        for {
          i <- (1 to 10).cps
          j <- (i to 10).cps
        } yield (cut (j))
      }.toList
      s1 should be (expected)
      val s2 = {
        (1 to 10).cps flatMap ((i: Int) => (i to 10).cps) map (cut (_))
      }.toList
      s2 should be (expected)
    }
    kit.run ()
  }

  it should "run flatMap over each nested element immediately" in {
    val kit = CpsSpecKit.newSequentialKit
    import kit.scheduler.spawn

    val s = new Shifter [Int]
    var b = List.newBuilder [Int]
    spawn {
      for {
        i <- s
        j <- (i to 10).cps
      } (cut (b += j))
    }
    kit.run ()
    for (i <- 1 to 10) {
      s.put (i)
      kit.run ()
      b.result () should be (i to 10)
      b = List.newBuilder [Int]
    }}

  it should "support filter" in {
    val kit = CpsSpecKit.newSequentialKit
    import kit.scheduler.spawn

    val expected = (1 to 10) filter (_ % 2 == 0)
    spawn {
      val s1 = {
        for {
          i <- (1 to 10).cps
          if cut (i % 2 == 0)
        } yield (i)
      }.toList
      s1 should be (expected)
      val s2 = {
        (1 to 10).cps filter ((i: Int) => cut (i % 2 == 0))
      }.toList
      s2 should be (expected)
    }
    kit.run ()
  }

  it should "filter each element immediately" in {
    val kit = CpsSpecKit.newSequentialKit
    import kit.scheduler.spawn

    val s = new Shifter [Int]
    var x = 0
    spawn {
      for {
        i <- s
        if cut (i % 2 == 0)
      } (cut (x = i))
    }
    kit.run ()
    for (i <- 1 to 10) {
      s.put (i)
      kit.run ()
      if (i % 2 == 0) {
        x should be (i)
      }}}

  it should "support repeating methods" in {
    val kit = CpsSpecKit.newSequentialKit
    import kit.scheduler.spawn

    spawn {
      var i = 0
      val s = whilst (i < 10) {
        i += 1
        cut (i)
      }.toList
      s should be (1 to 10)
    }
    kit.run ()
  }}
