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

package com.treode.cps.scheduler

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.scalatest.Assertions
import com.treode.cps.stub.CpsSpecKit

trait SchedulerChecks extends Assertions {

  private class DistinguishedException extends Exception
  private def fatal = new DistinguishedException

  def checkRunsEachTaskExactlyOnce (factory: () => CpsSpecKit) {
    val kit = factory ()
    import kit.scheduler.spawn

    val n = 12
    var v = Vector.fill (n) (new AtomicInteger (0)) // Track tasks
    for (i <- 0 to n-1) (spawn (v (i).addAndGet (i))) // Add to detect double runs
    kit.run ()
    for (i <- 0 to n-1) (expectResult (i) (v (i).get ())) // Did each run once?
  }

  protected [this] def checkRunsEachTimerExactlyOnce (factory: () => CpsSpecKit) {
    val kit = factory ()
    import kit.scheduler.schedule

    val n = 12
    var v = Vector.fill (n) (new AtomicInteger (0)) // Track timers
    for (i <- 0 to n-1) (schedule (i, MILLISECONDS) (v (i).addAndGet (i))) // Add to detect double runs
    kit.run ()
    for (i <- 0 to n-1) (expectResult (i) (v (i).get)) // Did each run once?
  }

  def checkRunsNestedTasks (factory: () => CpsSpecKit) {
    val kit = factory ()
    import kit.scheduler.spawn

    val n = 4
    var v1 = Vector.fill (n) (new AtomicInteger (0))
    var v2 = Vector.fill (n, n) (new AtomicInteger (0))
    var v3 = Vector.fill (n, n, n) (new AtomicInteger (0))
    for (i <- 0 to n-1) {
      spawn {
        for (j <- 0 to n-1) {
          spawn {
            for (k <- 0 to n-1) {
              spawn {
                v3 (i) (j) (k).addAndGet (i*n*n + j*n + k)
              }}
            v2 (i) (j).addAndGet (i*n + j)
          }}
        v1 (i).addAndGet (i)
      }}

    kit.run ()
    for (i <- 0 to n-1) {
      expectResult (i) (v1 (i).get ())
      for (j <- 0 to n-1) {
        expectResult (i*n + j) (v2 (i) (j).get ())
        for (k <- 0 to n-1) {
          expectResult (i*n*n + j * n+k) (v3 (i) (j) (k).get ())
        }}}}}
