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

import com.treode.cps._
import com.treode.cps.io.GatheringByteChannel

trait OutputBuffer extends WritableBuffer {

  def flushableBytes: Int

  def flush (min: Int = flushableBytes): Int @thunk
}

private final class MemoryOutputBuffer (protected val buffer: Buffer)
extends OutputBuffer with BufferEnvoy {

  def flushableBytes: Int = 0

  def flush (min: Int = flushableBytes): Int @thunk = 0

  override def toString = "MemoryOutputBuffer {buffer: " + buffer.toString + "}"
}

private class OutputChannelBuffer (channel: GatheringByteChannel)
extends OutputBuffer with WritableBufferEnvoy {

  protected val buffer = PagedBuffer ()

  def flushableBytes = buffer.readableBytes

  def flush (min: Int): Int @thunk = {
    require (min <= flushableBytes, "Cannot flush more bytes than are available.")
    val src = buffer.readableByteBuffers
    var n = 0
    var i = 0
    while (channel.isOpen && n < min && i >= 0) {
      i = channel.write (src)
      require (i < Int.MaxValue)
      n += i
    }
    if (n > 0) buffer.readAt = buffer.readAt + n
    n
  }

  override def toString = "OutputChannelBuffer {buffer: " + buffer.toString + "}"
}

object OutputBuffer {

  def apply (channel: GatheringByteChannel): OutputBuffer = new OutputChannelBuffer (channel)

  def bridge: OutputBuffer with Buffer = new MemoryOutputBuffer (Buffer ())
}
