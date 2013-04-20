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

import java.nio.charset.Charset
import java.nio.ByteBuffer

trait ReadableBuffer extends ReadableStream {

  /** The index of the next byte to read. */
  def readAt: Int

  /** Set the index of the next byte to read, between 0 and `writeAt`. */
  def readAt_= (index: Int)

  /** The last readable index. */
  def writeAt: Int

  /** The number of bytes from `readAt` to `writeAt`. */
  def readableBytes: Int

  /** Clear all data, and set `readAt` and `writeAt` to 0. */
  def clear ()

  /** Discard the `length` bytes starting at byte 0, and adjust `readAt` and `writeAt`. */
  def discard (length: Int)

  /** Create a new view on the backing store.  The new buffer maintains its own `readAt`
    * and `writeAt` positions but shares the bytes with the original buffer.
    */
  def slice (index: Int, length: Int): ReadableBuffer

  def findByte (start: Int, end: Int, byte: Byte): Int

  /** Get the byte at `index` without affecting the read position. */
  def getByte (index: Int): Byte

  /** Get the next byte at `readAt` and increment the read position. */
  def readByte (): Byte

  /** Get the next `length` byte at `readAt` and advance the read position. */
  def readBytes (bytes: Array [Byte], offset: Int, length: Int)
}

private trait ReadableBufferEnvoy extends ReadableBuffer {
  protected val buffer: ReadableBuffer

  def readAt = buffer.readAt
  def readAt_= (index: Int)  =  buffer.readAt = index
  def writeAt = buffer.writeAt
  def readableBytes = buffer.readableBytes

  def clear () = buffer.clear ()
  def discard (length: Int) = buffer.discard (length)
  def slice (index: Int, length: Int) = buffer.slice (index, length)

  def findByte (start: Int, end: Int, byte: Byte) = buffer.findByte (start, end, byte)

  def getByte (index: Int) = buffer.getByte (index)
  def readByte () = buffer.readByte ()
  def readBytes (bytes: Array [Byte], offset: Int, length: Int) =
    buffer.readBytes (bytes, offset, length)
}

trait WritableBuffer extends WritableStream {

  /** The index of the next byte to read. */
  def readAt: Int

  /** The index of the next byte to write. */
  def writeAt: Int

  /** Set the index of the next byte to write, between `readAt` and `capacity`. */
  def writeAt_= (index: Int)

  /** The number of bytes from `writeAt` to `capacity`. */
  def writableBytes: Int

  /** Number of bytes that can be accommodated. */
  def capacity: Int

  /** Grow if necessary to accommodate at least `min` bytes. */
  def capacity (min: Int)

  /** Create a new view on the backing store.  The new buffer maintains its own `readAt`
    * and `writeAt` positions but shares the bytes with the original buffer.
    */
  def slice (index: Int, length: Int): WritableBuffer

  /** Set the byte at `index` without affecting the write position. */
  def setByte (index: Int, byte: Byte)

  /** Set the next byte at `writeAt` and increment the write position. */
  def writeByte (byte: Byte)

  /** Set the next `length` bytes at `writeAt` and advance the write position. */
  def writeBytes (bytes: Array [Byte], offset: Int, length: Int): Unit

  /** Set the bytes at `writeAt` and advance the write position. */
  override def writeBytes (bytes: ByteBuffer): Unit = {
    writeBytes (bytes.array, bytes.position, bytes.remaining)
    bytes.position (bytes.limit)
  }

  /** Set `length` bytes at `dstoff` without affecting the write position. */
  def setBytes (dstoff: Int, src: Array [Byte], srcoff: Int, length: Int)

  /** Set bytes at `dstoff` without affecting the write position. */
  def setBytes (dstoff: Int, src: ByteBuffer): Unit = {
    setBytes(dstoff, src.array, src.position, src.limit)
    src.position (src.limit)
  }}

private trait WritableBufferEnvoy extends WritableBuffer {

  protected val buffer: WritableBuffer

  def readAt = buffer.readAt
  def writeAt = buffer.writeAt
  def writeAt_= (index: Int)  =  buffer.writeAt = index
  def writableBytes = buffer.writableBytes

  def capacity = buffer.capacity
  def capacity (min: Int) = buffer.capacity (min)

  def slice (index: Int, length: Int) = buffer.slice (index, length)

  def setByte (index: Int, byte: Byte) = buffer.setByte (index, byte)

  def writeByte (byte: Byte) = buffer.writeByte (byte)

  def setBytes (dstoff: Int, src: Array [Byte], srcoff: Int, len: Int) =
    buffer.setBytes (dstoff, src, srcoff, len)

  def writeBytes (bytes: Array [Byte], offset: Int, length: Int): Unit =
    buffer.writeBytes (bytes, offset, length)

  override def writeBytes (bytes: ByteBuffer): Unit =
    buffer.writeBytes (bytes)
}

trait Buffer extends ReadableBuffer with WritableBuffer {

  override def slice (index: Int, length: Int): Buffer

  /** This buffer from readAt (inclusive) to writeAt (exclusive) as NIO ByteBuffers.  These
   * ByteBuffers will be new and independent of ByteBuffers from other calls to this method, so
   * they will have their own position, limit and capacity.  However, all ByteBuffers from this
   * method will share the backing store with this buffer, so changes made through setByte will be
   * visible across them all.
   */
  def readableByteBuffers: Array [ByteBuffer]

  /** This buffer starting from writeAt (inclusive) to capacity (exclusive) as NIO ByteBuffers.
   * ByteBuffers will be new and independent of ByteBuffers from other calls to this method, so
   * they will have their own position, limit and capacity.  However, all ByteBuffers from this
   * method will share the backing store with this buffer, so changes made through setByte will be
   * visible across them all.
   */
  def writableByteBuffers: Array [ByteBuffer]
}

private trait BufferEnvoy extends Buffer with ReadableBufferEnvoy with WritableBufferEnvoy {

  protected val buffer: Buffer

  override def readAt = buffer.readAt
  override def writeAt = buffer.writeAt
  override def slice (index: Int, length: Int): Buffer = buffer.slice (index, length)

  def readableByteBuffers: Array [ByteBuffer] = buffer.readableByteBuffers
  def writableByteBuffers: Array [ByteBuffer] = buffer.writableByteBuffers
}

object Buffer {

  def apply (): Buffer = PagedBuffer ()

  def copy (bytes: Array [Byte]): Buffer = {
    val b = Buffer ()
    b.writeBytes (bytes, 0, bytes.length)
    b
  }}
