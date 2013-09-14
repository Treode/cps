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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions._
import scala.collection.mutable
import org.scalatest.PropSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.PropertyChecks
import org.scalatest.matchers.ShouldMatchers
import com.treode.cps.scalatest.CpsPropSpec
import com.treode.cps.stub.scheduler.TestScheduler

class MailboxSpec extends CpsPropSpec {

  property ("A Mailbox delivers every message exactly once with the random scheduler") {
    forAll (seeds) { seed: Long =>
      implicit val scheduler = TestScheduler.random (seed)
      import scheduler.spawn

      val n = 12
      val log = mutable.Set [Int] ()
      val mailbox = Mailbox [Int] ()
      for (i <- 1 to n) {
        spawn {
          mailbox.send (2*i - 1)
          mailbox.send (2*i)
        }
        spawn {
          assert (log.add (mailbox.receive ()))
          assert (log.add (mailbox.receive ()))
        }}
      scheduler.run ()
      expectResult ((1 to 2*n).toSet) (log)
    }}

  property ("A Mailbox delivers every message exactly once with the multithreaded scheduler") {
    val n = 1000
    val latch = new AtomicInteger (n)

    implicit val scheduler = TestScheduler.multithreaded()
    import scheduler.spawn

    val log = new ConcurrentHashMap [Int, String] ()
    val mailbox = Mailbox [Int] ()
    for (i <- 1 to n) {
      spawn {
        mailbox.send (2*i - 1)
        mailbox.send (2*i)
      }
      spawn {
        val m1 = mailbox.receive ()
        assert (log.putIfAbsent (m1, "") == null)
        val m2 = mailbox.receive ()
        assert (log.putIfAbsent (m2, "") == null)
        latch.decrementAndGet
      }}
    scheduler.run ()
    expectResult ((1 to 2*n).toSet) (log .map (_._1) .toSet)
  }}
