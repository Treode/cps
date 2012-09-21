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
import java.nio.channels.{
  AsynchronousFileChannel => JFile,
  CompletionHandler}
import java.nio.file.{OpenOption, Path}
import java.nio.file.attribute.FileAttribute
import scala.collection.JavaConversions._
import com.treode.cps.scheduler.Scheduler

private class FileLive (scheduler: Scheduler, file: JFile) extends File {
  import scheduler.suspend

  def close (): Unit @thunk = cut (file.close ())

  def isOpen: Boolean = file.isOpen ()

  def read (dst: ByteBuffer, pos: Long): Int @thunk =
    suspend {t: Thunk [Int] => file.read (dst, pos, null, new IntHandler (t))}

  def write (src: ByteBuffer, pos: Long): Int @thunk =
    suspend {t: Thunk [Int] => file.write (src, pos, null, new IntHandler (t))}
}

object FileLive {

  def open (scheduler: Scheduler, path: Path, opts: OpenOption*): File =
    new FileLive (scheduler, JFile.open (path, setAsJavaSet (Set (opts: _*)), null))
}
