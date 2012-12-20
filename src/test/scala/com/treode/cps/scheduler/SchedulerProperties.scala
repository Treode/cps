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

import com.treode.cps.scalatest.CpsPropSpec
import com.treode.cps.stub.scheduler.TestScheduler

private [cps] trait SchedulerProperties extends SchedulerChecks {
  this: CpsPropSpec =>

  def name: String
  def factory (seed: Long): () => TestScheduler

  property ("A " + name + " runs each task exactly once") {
    forAll ("seed") { seed: Int =>
      try {
      checkRunsEachTaskExactlyOnce (factory (seed))
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    }}

  property ("A " + name + " runs nested tasks") {
    forAll (seeds) { seed: Long =>
      checkRunsNestedTasks (factory (seed))
    }}}
