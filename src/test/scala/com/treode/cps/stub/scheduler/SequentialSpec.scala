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

import org.scalatest.FlatSpec

class SequentialSpec extends FlatSpec with SchedulerBehaviors {

  "A Scheduler with a sequential Executor" should behave like aScheduler (
      () => TestScheduler.sequential())

  it should "not run timers when they are turned off" in {
    checkDoesNotRunTimersWhenTheyAreOff (TestScheduler.sequential())
  }}
