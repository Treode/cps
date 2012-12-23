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

package com.treode.cps.stub.io

import java.nio.ByteBuffer
import java.nio.channels._
import scala.util.Random
import com.treode.cps.{Thunk, thunk}
import com.treode.cps.scheduler.Scheduler
import com.treode.cps.sync.AtomicState

private class Simplex (random: Random, val scheduler: Scheduler) extends AtomicState {
  import scheduler.suspend

  initialize (Empty)

  private [this] val self: Simplex.this.type = this

  private [this] def readPending = throw new ReadPendingException
  private [this] def writePending = throw new WritePendingException

  private [this] case class Point (buf: ByteBuffer, k: Thunk [Int], n: Int, i: Int)

  protected [this] trait State {

    def read (r: Point): Option [Unit]

    def write (w: Point): Option [Unit]

    def close (): Option [Unit]

    def deliver (r: Point, w: Point): Option [Unit] =
      throw new AssertionError ("Cannot deliver when not delivering.")
  }

  private [this] object Empty extends State {

    def read (r: Point) = move (this, new HaveReader (r)) (())

    def write (w: Point) = move (this, new HaveWriter (w)) (())

    def close () = move (this, Closed) (())
  }

  private [this] class HaveReader (r: Point) extends State {

    def read (r: Point) = readPending

    def write (w: Point) = move (this, Delivering) (self.deliver (r, w))

    def close () = move (this, Closed) (r.k (-1))
  }

  private [this] class HaveWriter (w: Point) extends State {

    def read (r: Point) = move (this, Delivering) (self.deliver (r, w))

    def write (w: Point) = writePending

    def close () = move (this, Closed) (w.k (-1))
  }

  private [this] object Delivering extends State {

    def read (r: Point) = readPending

    def write (w: Point) = writePending

    def close () = move (this, Closed) (())

    override def deliver (r: Point, w: Point) = {
      def copy (n: Int) = for (i <- 1 to n) (r.buf.put (w.buf.get))
      if (r.i < w.i) {
        copy (r.i)
        move (this, new HaveWriter (Point (w.buf, w.k, w.n, w.i - r.i))) (r.k (r.n))
      } else {
        copy (w.i)
        move (this, Empty) { r.k (r.n - r.i + w.i); w.k (w.n) }
      }}}

  private [this] object Closed extends State {

    def read (r: Point) = effect (r.k (-1))

    def write (w: Point) = effect (w.k (-1))

    override def deliver (r: Point, w: Point) =
      effect {
        r.k (-1)
        w.k (-1)
      }

    def close () = effect (())
  }

  private def deliver (r: Point, w: Point) = delegate (_.deliver (r, w))

  private val min = 100
  private val max = 1000

  def read (dst: ByteBuffer): Int @thunk =
    suspend [Int] { k =>
      // Our packet size for this time, somewhere between min and max.
      val r = random.nextInt (max + 1)
      val n = math.min (dst.remaining, math.max (min, r))
      if (n == 0) k (0) else delegate (_.read (Point (dst, k, n, n)))
    }

  def write (src: ByteBuffer): Int @thunk =
    suspend [Int] { k =>
      val r = random.nextInt (max + 1)
      val n = math.min (src.remaining, math.max (min, r))
      if (n == 0) k (0) else delegate (_.write (Point (src, k, n, n)))
    }

  def close (): Unit = delegate (_.close())
}
