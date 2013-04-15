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

package com.treode.cps
package io

import java.nio.ByteBuffer

/** Adapt Java's [[http://docs.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousFileChannel.html AsynchronousFileChannel]]
 * to Scala's continuations.
 */
trait File extends Channel {

  /** Read from the file into the buffer.
   * See [[http://docs.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousFileChannel.html#read (java.nio.ByteBuffer, long, A, java.nio.channels.CompletionHandler) read]]
   * of Java's AsynchronousFileChannel for details.
   */
  def read (dst: ByteBuffer, pos: Long): Int @thunk

  /** Write from the buffer into the file.
   * See [[http://docs.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousFileChannel.html#write (java.nio.ByteBuffer, long, A, java.nio.channels.CompletionHandler) write]]
   * of Java's AsynchronousFileChannel for details.
   */
  def write (src: ByteBuffer, pos: Long): Int @thunk
}
