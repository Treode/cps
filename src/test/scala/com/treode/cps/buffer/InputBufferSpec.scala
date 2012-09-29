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

package com.treode.cps.buffer

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import scala.util.continuations.reset
import com.treode.cps.stub.io.ByteChannelStub

class InputBufferSpec extends FlatSpec {
  import ByteChannelStub._

  val request = "GET /index.html HTTP/1.1\r\nContent-Length: 0\r\n\r\n"

  "readLine" should "find the end of the line" in {
    reset {
      val buf = InputBuffer (readable (request))
      expectResult (26) (buf.readLine (Int.MaxValue))
      buf.readAt = 26
      expectResult (45) (buf.readLine (Int.MaxValue))
      buf.discard (26)
      expectResult (19) (buf.readLine (Int.MaxValue))
    }}

  it should "work its way through staggering" in {
    reset {
      val buf = InputBuffer (stagger (readable (request), 23, 1, 1, 1, 1))
      expectResult (26) (buf.readLine (Int.MaxValue))
      buf.readAt = 26
      expectResult (45) (buf.readLine (Int.MaxValue))
      buf.discard (26)
      expectResult (19) (buf.readLine (Int.MaxValue))
    }}

  "readHeader" should "find the end of the header" in {
    reset {
      val buf = InputBuffer (readable (request + request))
      expectResult (47) (buf.readHeader (Int.MaxValue))
      buf.readAt = 47
      expectResult (94) (buf.readHeader (Int.MaxValue))
      buf.discard (47)
      expectResult (47) (buf.readHeader (Int.MaxValue))
    }}

  it should "work its way through staggering" in {
    reset {
      val buf = InputBuffer (stagger (readable (request + request), 42, 1, 1, 1, 1, 1, 1))
      expectResult (47) (buf.readHeader (Int.MaxValue))
      buf.readAt = 47
      expectResult (94) (buf.readHeader (Int.MaxValue))
      buf.discard (47)
      expectResult (47) (buf.readHeader (Int.MaxValue))
    }}

  "readString" should "read the whole string" in {
    reset {
      val buf = InputBuffer (readable ("Hello World"))
      expectResult ("Hello World") (buf.readString (Int.MaxValue))
    }}

  it should "read the whole string when staggered" in {
    reset {
      val buf = InputBuffer (stagger (readable ("Hello World"), 4, 3))
      expectResult ("Hello World") (buf.readString (Int.MaxValue))
    }}

  "limit" should "limit reading of the source buffer" in {
    reset {
      // Prefetch everything from channel to test FixedBuffer.
      val buf = InputBuffer (readable ("Hello World"))
      buf.fill (1)
      val limit = buf.limit (6)
      expectResult ("Hello ") (limit.readString (Int.MaxValue))
      expectResult ("World") (buf.readString (Int.MaxValue))
    }}

  "limit" should "limit reading of the source buffer when staggered" in {
    reset {
      // Prefetch too little from channel to test BoundedBuffer.
      val buf = InputBuffer (stagger (readable ("Hello World"), 4, 1, 1))
      buf.fill (1)
      val limit = buf.limit (6)
      expectResult ("Hello ") (limit.readString (Int.MaxValue))
      expectResult ("World") (buf.readString (Int.MaxValue))
    }}
}
