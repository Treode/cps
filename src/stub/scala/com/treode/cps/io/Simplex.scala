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
package io

import java.nio.ByteBuffer
import java.nio.channels._
import scala.util.Random
import com.treode.cps.scheduler.Scheduler
import com.treode.cps.sync.AtomicState

private class Simplex (random: Random, val scheduler: Scheduler) extends AtomicState {
  import scheduler.suspend

  initialize (Empty)

  private [this] def readPending = throw new ReadPendingException
  private [this] def writePending = throw new WritePendingException

  private [this] case class Point (buf: ByteBuffer, k: Int => Unit, n: Int, i: Int)

  protected [this] trait State {
    def read (r: Point): Behavior [Unit]
    def write (w: Point): Behavior [Unit]
    def deliver (): Behavior [Unit] = illegalState ("Cannot deliver when not delivering.")
  }

  private [this] object Empty extends State {
    def read (r: Point) = moveTo (new HaveReader (r)) withoutEffect
    def write (w: Point) = moveTo (new HaveWriter (w)) withoutEffect
  }

  private [this] class HaveReader (r: Point) extends State {
    def read (r: Point) = readPending
    def write (w: Point) = moveTo (new Delivering (r, w)) withEffect (Simplex.this.deliver ())
  }

  private [this] class HaveWriter (w: Point) extends State {
    def read (r: Point) = moveTo (new Delivering (r, w)) withEffect (Simplex.this.deliver ())
    def write (w: Point) = writePending
  }

  private [this] class Delivering (r: Point, w: Point) extends State {
    def read (r: Point) = readPending
    def write (w: Point) = writePending

    private def copy (n: Int) = for (i <- 1 to n) (r.buf.put (w.buf.get))

    override def deliver () = {
      if (r.i < w.i) {
        copy (r.i)
        moveTo (new HaveWriter (Point (w.buf, w.k, w.n, w.i - r.i))) withEffect (r.k (r.n))
      } else {
        copy (w.i)
        moveTo (Empty) withEffect { r.k (r.n - r.i + w.i); w.k (w.n) }
      }}}

  private [this] def deliver () = delegate (_.deliver ())

  private val min = 100
  private val max = 1000

  def read (dst: ByteBuffer): Int @thunk =
    suspend { (k: Int => Unit) =>
      // Our packet size for this time, somewhere between min and max.
      val r = random.nextInt (max + 1)
      val n = math.min (dst.remaining, math.max (min, r))
      if (n == 0) k (0) else delegate (_.read (Point (dst, k, n, n)))
    }

  def write (src: ByteBuffer): Int @thunk =
    suspend { (k: Int => Unit) =>
      val r = random.nextInt (max + 1)
      val n = math.min (src.remaining, math.max (min, r))
      if (n == 0) k (0) else delegate (_.write (Point (src, k, n, n)))
    }}
