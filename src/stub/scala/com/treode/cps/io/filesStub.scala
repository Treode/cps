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
import java.nio.channels.ClosedChannelException
import java.nio.file.{OpenOption, Path}
import scala.collection.mutable
import com.treode.cps.buffer.Buffer

class FileStub private [io] (private [this] var buffer: Option [Buffer]) extends File {

  def close (): Unit @thunk = buffer = None

  def isOpen: Boolean = buffer.isDefined

  def read (dst: ByteBuffer, pos: Long): Int @thunk =
    buffer match {
      case Some (b) =>
        if (pos > b.writeAt)
          -1
        else {
          val len = math .min (dst.remaining, b.writeAt - pos) .toInt
          b.readBytes (dst, pos.toInt, len)
          len
        }

      case None =>
        throw new ClosedChannelException
    }

  def write (src: ByteBuffer, pos: Long): Int @thunk =
    buffer match {
      case Some (dst) =>
        val lim = pos.toInt + src.remaining
        if (lim > dst.capacity)
          dst.capacity (lim)
        if (dst.writeAt < lim)
          dst.writeAt = lim
        val len = src.remaining
        dst.setBytes (pos.toInt, src)
        len

      case None =>
        throw new ClosedChannelException
    }}

class FileSystemStub private {

  private [this] val files = mutable.Map [Path, Buffer] ()

  def size (path: Path) =
    files .get (path) .map (_.writeAt)

  def rename (from: Path, to: Path) {
    files.get (from) map { buffer =>
      files.remove (from)
      files.put (to, buffer)
    }}

  /** Ignores open options.  Creates a file if it doesn't exist and returns the current file if it
    * does exist, regardless of options.  Opens files for read and write, regardless of options.
    */
  def open (path: Path, opts: OpenOption*): File = {
    if (files contains path) {
      return new FileStub (Some (files (path)))
    } else {
      val buffer = Buffer()
      files.put (path, buffer)
      return new FileStub (Some (buffer))
    }}}

object FileSystemStub {

  def apply() = new FileSystemStub
}
