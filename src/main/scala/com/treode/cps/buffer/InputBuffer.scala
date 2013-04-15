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

package com.treode.cps.buffer

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import com.treode.cps._
import com.treode.cps.io.ScatteringByteChannel

trait InputBuffer extends ReadableBuffer {

  def fill (min: Int = 1): Int @thunk

  def limit (length: Int): InputBuffer

  def ensure (min: Int = 1) = {
    val n = readableBytes
    if (n < min) fill (min - n) else cut (0)
  }

  private [this] def isCr (c: Int) = (c == '\r')
  private [this] def isLf (c: Int) = (c == '\n')

  // To use a regular expression, we would need to convert the ByteBuffer to a String, and we don't
  // want to waste CPU on that. This implements a DFA to search for [CR]LF [CR]LF in a ByteBuffer.
  // It begins searching at the given position, and it returns the position that it searched upto
  // for restarting the search later. It also returns a flag indicating if [CR]LF [CR]LF was found.
  private [this] def findCrlfCrlf (start: Int): (Int, Boolean) = {
    var pos = start
    var n = findByte (pos, writeAt, '\n')
    while (n >= 0) {
      pos = n
      var i = pos + 1
      if (i < writeAt && isCr (getByteNRW (i)))
        i += 1
      if (i < writeAt) {
        if (isLf (getByteNRW (i))) {
          i += 1
          return (i, true)
        }
      } else {
        return (pos, false)
      }
      n = findByte (pos + 1, writeAt, '\n')
    }
    (writeAt, false)
  }

  /** Read until a blank line, or connection close, or `max` bytes.  A blank line is identified by
   * the ASCII bytes [CR]LF [CR]LF, that is one or both CR may be missing.
   */
  def readHeader (max: Int): Int @thunk = {
    val x = findCrlfCrlf (readAt)
    var pos = x._1
    var end = x._2
    while (!end && pos - readAt < max) {
      if (pos - readAt > max) throw new Exception ("Headers too long.")
      val n = fill ()
      if (n < 0) throw new ClosedChannelException
      val x = findCrlfCrlf (pos)
      pos = x._1
      end = x._2
    }
    pos
  }

  // Search to [CR]LF.
  private [this] def findCrlf (start: Int): (Int, Boolean) = {
    var pos = start
    var n = findByte (pos, writeAt, '\n')
    if (n < 0) (writeAt, false) else (n, true)
  }

  /** Read a line, or until connection close, or until `max` bytes.  The end of the line is
   * identified by the ASCII bytes [CR]LF, that is the CR may be missing.
   */
  def readLine (max: Int): Int @thunk = {
    val x = findCrlf (readAt)
    var pos = x._1
    var end = x._2
    while (!end) {
      if (pos - readAt > max) throw new Exception ("Line too long.")
      val n = fill ()
      if (n < 0) throw new Exception ("Connection closed.")
      val x = findCrlf (pos)
      pos = x._1
      end = x._2
    }
    pos + 1
  }

  def readAll (max: Int): Unit @thunk = {
    // When written as a while loop, this was broken.  I suspect CPS.
    var n = fill (1)
    if (n > 0) readAll (max - n) else cut ()
  }

  private [this] def concat (): Array [Byte] = {
    val dst = new Array [Byte] (readableBytes)
    readBytes (dst, 0, readableBytes)
    dst
  }

  def readString (max: Int, cs: Charset = UTF_8): String @thunk = {
    // TODO: decode one ByteBuffer at a time, to ease memory pressure.
    readAll (max)
    val src = ByteBuffer.wrap (concat ())
    cs .decode (src) .toString
  }}

private final class FixedInputBuffer (protected val buffer: Buffer)
extends InputBuffer with ReadableBufferEnvoy {

  def fill (min: Int) = cut (-1)

  def limit (length: Int) = {
    new FixedInputBuffer (buffer.slice (readAt, math.min (length, buffer.readableBytes)))
  }

  override def toString = "FixedBuffer {buffer: " + buffer.toString + "}"
}

private final class BoundedInputBuffer (protected val buffer: InputBuffer, limit: Int)
extends InputBuffer with ReadableBufferEnvoy {

  private [this] var remaining = math.max (0, limit - buffer.readableBytes)

  def fill (min: Int): Int @thunk = {
    if (remaining > 0) {
      val n = buffer.fill (math.min (min, remaining))
      if (n > remaining) {
        remaining = 0
        remaining
      } else {
        remaining -= n
        n
      }
    } else -1
  }

  def limit (length: Int): InputBuffer = {
    require (length <= remaining, "New limit must preceed end of buffer.")
    buffer.limit (length)
  }

  override def toString =
    "BoundedChannelBuffer {limit: " + limit + ", delegate: " + buffer.toString + "}"
}

private final class InputChannelBuffer (channel: ScatteringByteChannel)
extends InputBuffer with ReadableBufferEnvoy {

  protected val buffer = PagedBuffer ()

  def fill (min: Int): Int @thunk = {
    buffer.capacity (buffer.writeAt + min)
    val dst = buffer.writableByteBuffers
    var n = 0
    var i = 0
    while (channel.isOpen && n < min && i >= 0) {
      i = channel.read (dst)
      require (i < Int.MaxValue)
      n += i
    }
    if (n > 0) buffer.writeAt = buffer.writeAt + n
    n
  }

  def limit (length: Int) = {
    if (length <= buffer.readableBytes) {
      val limited = new FixedInputBuffer (buffer.slice (0, length))
      buffer.readAt = length
      limited
    } else {
      new BoundedInputBuffer (this, length)
    }}

  override def toString = "SimpleChannelBuffer {buffer: " + buffer.toString + "}"
}

private final class ChunkedChannelBuffer (
  protected val buffer: InputBuffer,
  parse: Unit => Int @thunk
) extends InputBuffer with ReadableBufferEnvoy {

  private [this] var limit = 0

  def fill (min: Int): Int @thunk = {
    if (limit > 0) {
      val n = buffer.fill (math.min (min, limit))
      if (n > limit) limit else n
    } else if (limit == 0) {
      limit = parse ()
      val n = buffer.fill (math.min (min, limit))
      if (n > limit) limit else n
    } else -1
  }

  def limit (length: Int) = throw new UnsupportedOperationException
}

object InputBuffer {

  def apply (channel: ScatteringByteChannel): InputBuffer = new InputChannelBuffer (channel)

  val empty: InputBuffer = new FixedInputBuffer (Buffer.copy (Array [Byte] ()))

  def copy (bytes: Array [Byte]): InputBuffer = new FixedInputBuffer (Buffer.copy (bytes))

  def copy (s: String, cs: Charset = Charset.defaultCharset): InputBuffer = copy (s.getBytes (cs))

  def chunk (buffer: InputBuffer, parse: Unit => Int @thunk): InputBuffer =
    new ChunkedChannelBuffer (buffer, parse)

  def bridge (src: Buffer): InputBuffer = new FixedInputBuffer (src.slice (0, src.writeAt))
}
