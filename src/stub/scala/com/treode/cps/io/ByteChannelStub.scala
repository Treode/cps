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

package com.treode.cps.io

import java.nio.{ByteBuffer, CharBuffer}
import java.nio.channels.ClosedChannelException
import java.nio.charset.Charset
import com.treode.cps._

private class ByteArrayInputChannel (bytes: Array [Byte]) extends ScatteringByteChannel {

  private var pos = 0
  private var _isOpen = true

  def close (): Unit @thunk = _isOpen = false

  def isOpen: Boolean = _isOpen

  def read (dst: ByteBuffer): Int @thunk = {
    if (!_isOpen) {
      cut
      throw new ClosedChannelException
    } else if (pos < bytes.length) {
      val length = math.min (dst.remaining, bytes.length - pos)
      dst.put (bytes, pos, length)
      pos += length
      cut (length)
    } else {
      cut (-1)
    }}

  def read (dst: Array [ByteBuffer]): Long @thunk = {
    var (i, l, n) = (0, 0, 0)
    while (n >= 0 && i < dst.length) {
      val x = read (dst (i))
      n = x
      if (n >= 0) l += n
      i += 1
    }
    if (l > 0) l.toLong else n.toLong
  }}

private class StringOutputChannel (bldr: StringBuilder, cs: Charset) extends GatheringByteChannel {

  private val dec = cs.newDecoder
  private var _isOpen = true

  def close (): Unit @thunk = {
    // Get any remaining state from the decoder.
    val src = ByteBuffer.allocate (8)
    val dst = CharBuffer.allocate (8)
    dec.decode (src, dst, true)
    bldr ++= dst.toString
    _isOpen = false
  }

  def isOpen: Boolean = _isOpen

  def write (src: ByteBuffer): Int @thunk = {
    if (!_isOpen) throw new ClosedChannelException
    val start = src.position
    val dst = CharBuffer.allocate (src.limit - start)
    dec.decode (src, dst, false)
    dst.flip ()
    bldr ++= dst.toString
    src.position - start
  }

  def write (src: Array [ByteBuffer]): Long @thunk = {
    var (i, l, n) = (0, 0, 0)
    while (n >= 0 && i < src.length) {
      val x = write (src (i))
      n = x
      if (n >= 0) l += n
      i += 1
    }
    if (l > 0) l.toLong else n.toLong
  }}

private class StaggeredInputChannel (c: ScatteringByteChannel, var ls: List [Int])
extends ScatteringByteChannel {

  def close (): Unit @thunk = c.close ()

  def isOpen: Boolean = c.isOpen

  def read (dst: ByteBuffer): Int @thunk = {
    ls.headOption match {
      case Some (limit) if limit < dst.limit - dst.position =>
        // pos/limit are fiddly; that's why we went with readAt/writeAt in the CPS buffers.
        val save = dst.limit
        dst.limit (dst.position + limit)
        val n = c.read (dst)
        ls = if (n < limit) (limit - n.toInt) :: ls.tail else ls.tail
        dst.limit (save)
        n
      case Some (limit) =>
        val n = c.read (dst)
        ls = if (n < limit) (limit - n.toInt) :: ls.tail else ls.tail
        n
      case None =>
        c.read (dst)
    }}

  def read (dst: Array [ByteBuffer]): Long @thunk = {
    val len = dst .map (b => b.limit - b.position) .sum
    ls.headOption match {
      case Some (limit) if limit < len =>
        val saves = dst map (_.limit)
        var (i, l) = (0, limit)
        while (dst (i) .limit - dst (i) .position < l) {
          l -= dst (i) .limit - dst (i) .position
          i += 1
        }
        dst (i) .limit (dst (i) .position + l)
        i += 1
        while (i < dst.length) {
          dst (i) .limit (0)
          i += 1
        }
        val n = c.read (dst)
        ls = if (n < limit) (limit - n.toInt) :: ls.tail else ls.tail
        for (i <- 0 until dst.length) dst (i) .limit (saves (i))
        n
      case Some (l) =>
        val n = c.read (dst)
        ls = if (n < l) (l - n.toInt) :: ls.tail else ls.tail
        n
      case None =>
        c.read (dst)
    }}}

private class StaggeredOutputChannel (c: GatheringByteChannel, var ls: List [Int])
extends GatheringByteChannel {

  def close (): Unit @thunk = c.close ()

  def isOpen: Boolean = c.isOpen

  def write (src: ByteBuffer): Int @thunk = {
    ls.headOption match {
      case Some (limit) if limit < src.limit - src.position =>
        // pos/limit are fiddly; that's why we went with readAt/writeAt in the CPS buffers.
        val save = src.limit
        src.limit (src.position + limit)
        val n = c.write (src)
        ls = if (n < limit) (limit - n.toInt) :: ls.tail else ls.tail
        src.limit (save)
        n
      case Some (limit) =>
        val n = c.write (src)
        ls = if (n < limit) (limit - n.toInt) :: ls.tail else ls.tail
        n
      case None =>
        c.write (src)
    }}

  def write (src: Array [ByteBuffer]): Long @thunk = {
    val len = src .map (b => b.limit - b.position) .sum
    ls.headOption match {
      case Some (limit) if limit < len =>
        val saves = src map (_.limit)
        var (i, l) = (0, limit)
        while (src (i) .limit - src (i) .position < l) {
          l -= src (i) .limit - src (i) .position
          i += 1
        }
        src (i) .limit (src (i) .position + l)
        i += 1
        while (i < src.length) {
          src (i) .limit (0)
          i += 1
        }
        val n = c.write (src)
        ls = if (n < limit) (limit - n.toInt) :: ls.tail else ls.tail
        for (i <- 0 until src.length) src (i) .limit (saves (i))
        n
      case Some (l) =>
        val n = c.write (src)
        ls = if (n < l) (l - n.toInt) :: ls.tail else ls.tail
        n
      case None =>
        c.write (src)
    }}}

object ByteChannelStub {

  /** Read from the string as though it were a stream of bytes. */
  def readable (s: String, cs: Charset = Charset.defaultCharset): ScatteringByteChannel =
    new ByteArrayInputChannel (s.getBytes (cs))

  /** Write to the builder as though it were a stream of bytes. */
  def writable (bldr: StringBuilder, cs: Charset = Charset.defaultCharset): GatheringByteChannel =
    new StringOutputChannel (bldr, cs)

  /** Read deliberately breaking read at the given points.  This helps test methods that must
   * loop over write waiting for complete input. */
  def stagger (c: ScatteringByteChannel, ps: Int*): ScatteringByteChannel =
    new StaggeredInputChannel (c, ps.toList)

  /** Write deliberately breaking write at the given points.  This helps test methods that must
   * loop over write waiting for complete input. */
  def stagger (c: GatheringByteChannel, ps: Int*): GatheringByteChannel =
    new StaggeredOutputChannel (c, ps.toList)

}
