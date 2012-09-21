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

package com.treode.collection.mutable

import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import scala.collection.mutable.Stack

class ChoosyQueueSpec extends WordSpec with ShouldMatchers {

  "A ChoosyQueue" when {
    "empty" should {
      val q = ChoosyQueue [Int] ()
      "be empty" in {
        q.isEmpty should be (true)
      }
      "complain on indexed dequeue" in {
        evaluating { q.dequeue (0) } should produce [IndexOutOfBoundsException]
      }
      "match nothing on predicated dequeue" in {
        q.dequeue (_ => true) should be (None)
      }}

    "non-empty" should {
      val q = ChoosyQueue [Int] ()
      q.enqueue (10)
      q.enqueue (20)
      q.enqueue (30)
      q.enqueue (40)
      "be non-empty" in {
        q.isEmpty should be (false)
      }
      "complain on out of bounds indexed dequeue" in {
        evaluating { q.dequeue (4) } should produce [IndexOutOfBoundsException]
      }
      "allow dequeuing in the middle" in {
        q.dequeue (1) should be (20)
        q.isEmpty should be (false)
      }
      "dequeue something by matching predicate" in {
        q.dequeue (_ == 30) should be (Some (30))
        q.isEmpty should be (false)
      }
      "dequeue nothing by non-matching predicate" in {
        q.dequeue (_ == 50) should be (None)
        q.isEmpty should be (false)
      }}}}
