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
import java.nio.file.{Files, Path, Paths, OpenOption, StandardOpenOption}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import org.scalatest.Assertions
import com.treode.cps.scheduler.Scheduler

import StandardOpenOption._

trait FileChecks {
  this: Assertions =>

  trait FileSpecKit {

    val scheduler: Scheduler
    import scheduler.spawn

    def openFile (path: Path, opts: OpenOption*): File

    /** Run until the system is quiet and the condition is false. */
    def run (cond: => Boolean): Unit
  }

  def checkOpenWriteCloseOpenRead (kit: FileSpecKit) {
    import kit.{openFile, scheduler}
    import kit.scheduler.spawn

    val latch = new AtomicInteger (1)

    def writeInt (f: File, i: Int, p: Int): Unit @thunk = {
      val buf = ByteBuffer.allocate (4)
      buf.putInt (i)
      buf.flip ()
      var n = 0
      while (n < 4) (n += f.write (buf, p))
      ()
    }

    def writeStr (f: File, str: String, pos: Int): Unit @thunk = {
      val buf = ByteBuffer.wrap (str.getBytes)
      val len = buf.limit
      writeInt (f, len, pos)
      var n = 0
      while (n < len) (n += f.write (buf, pos + 4))
      expectResult (len) (n)
    }

    def readInt (f: File, p: Int) = {
      val buf = ByteBuffer.allocate (4)
      var n = 0
      while (n < 4) (n += f.read (buf, p))
      buf.flip ()
      val i = buf.getInt ()
      i
    }

    def readStr (f: File, p: Int, expected: String): Unit @thunk = {
      val len = readInt (f, p)
      val buf = ByteBuffer.allocate (len)
      var n = 0
      while (n < len) (n += f.read (buf, p + 4))
      val message = new String (buf.array, 0, len)
      expectResult (expected) (message)
    }

    spawn {
      val f1 = openFile (Paths.get ("test-file"), WRITE, CREATE)
      writeStr (f1, speech, 0)
      f1.close()
      val f2 = openFile (Paths.get ("test-file"), READ)
      readStr (f2, 0, speech)
      f2.close()
      latch.getAndDecrement ()
    }

    kit.run (latch.get () > 0)
    Files.deleteIfExists (Paths.get ("test-file"))
  }}
