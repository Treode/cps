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

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import com.treode.cps.stub.CpsSpecKit

class SequentialSpec extends FlatSpec with SchedulerBehaviors {

  "A Scheduler with a sequential Executor" should behave like aScheduler (() => CpsSpecKit.newSequentialKit)
}
