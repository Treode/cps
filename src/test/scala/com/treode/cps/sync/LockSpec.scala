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

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import org.scalatest.{FlatSpec, PropSpec, Specs}
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.PropertyChecks

class LockSpec extends Specs (LockBehaviors, LockProperties)

private object LockBehaviors extends CpsFlatSpec {
  private [this] class DistinguishedException extends Exception
  private [this] def fatal: Unit = throw new DistinguishedException

  "A Lock" should "pass an exception through" during {
    val log = withLog ("mark")
    val kit = withCpsKit (CpsSpecKit.newSequentialKit)

    import kit.scheduler
    import kit.scheduler.spawn

    val lock = Lock (scheduler)
    spawn {
      interceptCps [DistinguishedException] (lock.exclusive (fatal))
      log ("mark")
    }}}

private object LockProperties extends CpsPropSpec {

  property ("A lock runs each critical section once atomically with the random scheduler") {
    forAll (seeds) { seed: Long =>
      withWrappers {
        val m = 17
        val n = 11
        val log = withLog ((0 until m * n) map (_.toString): _*)
        val kit = withCpsKit (CpsSpecKit.newRandomKit (seed))

        import kit.scheduler
        import kit.scheduler.{cede, spawn, suspend}

        val lock = Lock (scheduler)
        for (i <- 0 until m) {
          spawn {
            lock.exclusive {
              for (j <- (0 until n).cps) {
                log ((n * i + j).toString)
                cede ()
              }}}}}}}

  property ("A lock runs each critical section once atomically with the multithreaded scheduler") {
    withWrappers {
      val m = 43
      val n = 37
      val latch = new AtomicInteger (m * n)
      val log = withLog ((0 until m * n) map (_.toString): _*)
      val kit = withCpsKit (CpsSpecKit.newMultihreadedKit (latch.get > 0))

      import kit.scheduler
      import kit.scheduler.{cede, spawn, suspend}

      val lock = Lock (scheduler)
      for (i <- 0 until m) {
        spawn {
          lock.exclusive {
            for (j <- (0 until n).cps) {
              log ((n * i + j).toString)
              cede ()
              latch.getAndDecrement
            }}}}}}}
