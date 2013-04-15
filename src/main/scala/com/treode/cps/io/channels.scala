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

import java.net.{SocketAddress, SocketOption}
import java.nio.ByteBuffer

/** Adapt Java's [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousChannel.html AsynchronousChannel]]
 * to Scala's continuations.
 */
trait Channel {

  /** Close this channel.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousChannel.html#close () close]]
   * of Java's AsynchronousChannel for details.
   */
  def close (): Unit @thunk

  /** Is this channel open? */
  def isOpen: Boolean
}

/** Adapt Java's [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/NetworkChannel.html NetworkChannel]]
 * to Scala's continuations.
 */
trait NetworkChannel extends Channel {

  /** Return the value of the option.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/NetworkChannel.html#getOption (java.net.SocketOption) getOption]]
   * of Java's NetworkChannel for details.
   */
  def getOption [A] (name: SocketOption [A]): A

  /** Get the local address.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/NetworkChannel.html#getLocalAddress () getLocalAddress]]
   * of Java's NetworkChannel for details.
   * @return `Some (address)` if the socket is bound, or `None` otherwise.
   */
  def localAddress: Option [SocketAddress]

  /** Set the value of the option.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/NetworkChannel.html#setOption (java.net.SocketOption, T) setOption]]
   * of Java's NetworkChannel for details.
   */
  def setOption [A] (name: SocketOption [A], value: A): this.type

  /** The options supported by this socket.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/NetworkChannel.html#supportedOptions () supportedOptions]]
   * of Java's NetworkChannel for details.
   */
  def supportedOptions: Set [SocketOption [_]]
}

/** The read portion of Java's [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousByteChannel.html AsynchronousByteChannel]]
 * adapted to Scala's continuations.
 */
trait ReadableByteChannel extends Channel {

  /** Read from the channel into the buffer.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousByteChannel.html#read (java.nio.ByteBuffer, A, java.nio.channels.CompletionHandler) read]]
   * of Java's AsynchronousByteChannel for details.
   */
  def read (dst: ByteBuffer): Int @thunk
}

/** The scattering portion of Java's [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousByteChannel.html AsynchronousByteChannel]]
 * adapted to Scala's continuations.
 */
trait ScatteringByteChannel extends ReadableByteChannel {

  /** Read from the channel into the buffer.
   * See [[http://docs.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousSocketChannel.html#read (java.nio.ByteBuffer [], int, int, long, java.util.concurrent.TimeUnit, A, java.nio.channels.CompletionHandler) read]]
   * of Java's AsynchronousByteChannel for details.
   */
  def read (dst: Array [ByteBuffer]): Long @thunk
}

/** The write portiong of Java's [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousByteChannel.html AsynchronousByteChannel]]
 * adapted to Scala's continuations.
 */
trait WritableByteChannel extends Channel {

  /** Write from the buffer to the channel.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousByteChannel.html#write (java.nio.ByteBuffer, A, java.nio.channels.CompletionHandler) write]]
   * of Java's AsynchronousByteChannel for details.
   */
  def write (src: ByteBuffer): Int @thunk
}

trait GatheringByteChannel extends WritableByteChannel {

  /** Write from the buffer to the channel.
   * See [[http://docs.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousSocketChannel.html#write (java.nio.ByteBuffer [], int, int, long, java.util.concurrent.TimeUnit, A, java.nio.channels.CompletionHandler) write]]
   * of Java's AsynchronousByteChannel for details.
   */
  def write (src: Array [ByteBuffer]): Long @thunk
}

/** Adapt Java's [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousByteChannel.html AsynchronousByteChannel]]
 * to Scala's continuations.
 */
trait ByteChannel extends ScatteringByteChannel with GatheringByteChannel
