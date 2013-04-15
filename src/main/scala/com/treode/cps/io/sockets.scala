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

import java.net.SocketAddress
import java.nio.ByteBuffer

/** Adapt Java's [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousSocketChannel.html AsynchronousSocketChannel]]
 * to Scala's continuations.
 */
trait Socket extends ByteChannel with NetworkChannel {

  /** Connect this channel to a remote peer.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousSocketChannel.html#connect (java.net.SocketAddress, A, java.nio.channels.CompletionHandler) connect]]
   * of Java's AsynchronousSocketChannel for details.
   */
  def connect (remote: SocketAddress): Unit @thunk

  /** Return the remote address to which this socket is connected.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousSocketChannel.html#getRemoteAddress () getRemoteAddress]]
   * of Java's AsynchronousSocketChannel for details.
   * @return `Some (address)` if this socket is connected, `None` otherwise.
   */
  def remoteAddress: Option [SocketAddress]
}

/** Adapt Java's [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousServerSocketChannel.html AsynchronousServerSocketChannel]]
 * to Scala's continuations.
 */
trait ServerSocket extends NetworkChannel {

  /** Accept a connection.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousServerSocketChannel.html#accept (A, java.nio.channels.CompletionHandler) accept]]
   * of Java's AsynchronousServerSocketChannel for details.
   */
  def accept (): Socket @thunk

  /** Bind this socket to a local address and configure it to listen for connections.
   * See [[http://download.oracle.com/javase/7/docs/api/java/nio/channels/AsynchronousServerSocketChannel.html#bind (java.net.SocketAddress, int) bind]]
   * of Java's AsynchronousServerSocketChannel for details.
   */
  def bind (local: SocketAddress, backlog: Int = 0): this.type
}
