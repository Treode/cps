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

package com.treode.cps.stub.scheduler

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.scalatest.Assertions
import com.treode.cps.sync.Mailbox

trait SchedulerChecks extends Assertions {

  private class DistinguishedException extends Exception
  private def fatal = new DistinguishedException

  def checkRunsEachTaskExactlyOnce (factory: () => TestScheduler) {
    val scheduler = factory ()
    import scheduler.spawn

    val n = 12
    var v = Vector.fill (n) (new AtomicInteger (0)) // Track tasks
    for (i <- 0 to n-1) (spawn (v (i).addAndGet (i))) // Add to detect double runs
    scheduler.run ()
    for (i <- 0 to n-1) (expectResult (i) (v (i).get ())) // Did each run once?
  }

  protected [this] def checkRunsEachTimerExactlyOnce (factory: () => TestScheduler) {
    val scheduler = factory ()
    import scheduler.schedule

    val n = 12
    val v = Vector.fill (n) (new AtomicInteger (0)) // Track timers
    for (i <- 0 to n-1) (schedule (i, MILLISECONDS) (v (i).addAndGet (i))) // Add to detect double runs
    scheduler.run ()
    for (i <- 0 to n-1) (expectResult (i) (v (i).get)) // Did each run once?
  }

  protected [this] def checkDoesNotRunTimersWhenTheyAreOff (scheduler: TestScheduler) {
    import scheduler.schedule

    val v = new AtomicInteger (0)
    schedule (1, MILLISECONDS) (v.addAndGet (1))
    scheduler.run ()
    expectResult (0) (v.get)
  }

  def checkRunsNestedTasks (factory: () => TestScheduler) {
    val scheduler = factory ()
    import scheduler.spawn

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

    scheduler.run ()
    for (i <- 0 to n-1) {
      expectResult (i) (v1 (i).get ())
      for (j <- 0 to n-1) {
        expectResult (i*n + j) (v2 (i) (j).get ())
        for (k <- 0 to n-1) {
          expectResult (i*n*n + j * n+k) (v3 (i) (j) (k).get ())
        }}}}

  def checkAwaitsTaskInThread (factory: () => TestScheduler) {
    implicit val scheduler = factory ()
    import scheduler.await

    val mbx = Mailbox [Int] ()
    mbx.send (3)
    val x = await (mbx.receive())
    expectResult (3) (x)
  }}
