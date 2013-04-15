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

import scala.collection.{generic, mutable}

/** Implicit conversions supporting `foreach`, `map`, etc. */
object CpsConversions {
  trait CpsMonad [A] {

    def foreach (f: A => Any @thunk): Unit @thunk

    def map [B] (f: A => B @thunk): CpsMonad [B] =
      new Mapper [A, B] (f, this)

    def flatMap [B] (f: A => CpsMonad [B] @thunk): CpsMonad [B] =
      new FlatMapper [A, B] (f, this)

    def filter (p: A => Boolean @thunk): CpsMonad [A] =
      new Filter [A] (p, this)

    def withFilter (p: A => Boolean @thunk) =
      filter (p)

    private def build [That] (b: mutable.Builder [A, That]): That @thunk = {
      foreach ((x: A) => b += x)
      b.result ()
    }

    def toList = build (List.newBuilder)
    def toOption = toList.headOption
    def toSeq = build (Seq.newBuilder)
  }

  private type Func [A, B] = A => B @thunk
  private type Flat [A, B] = A => CpsMonad [B] @thunk
  private type Pred [A] = A => Boolean @thunk
  private type Iter [A] = CpsMonad [A]

  private class Mapper [A, B] (g: Func [A, B], i: Iter [A]) extends CpsMonad [B] {
    def foreach (f: Func [B, Any]) = i.foreach ((x: A) => f (g (x)))
  }

  private class FlatMapper [A, B] (g: Flat [A, B], i: Iter [A]) extends CpsMonad [B] {
    def foreach (f: Func [B, Any]) = i.foreach ((x: A) => g (x).foreach (f))
  }

  private class Filter [A] (p: Pred [A], i: Iter [A]) extends CpsMonad [A] {
    def foreach (f: A => Any @thunk) = i.foreach { x: A =>
      // CPS does not like if here.
      // CPS should introduce val declaration.
      val b = p (x)
      var i = b
      while (i) {
        f (x)
        i = false
      }}}

  private [cps] class Repeater [A] (cond: => Boolean, g: => A @thunk) extends CpsMonad [A] {
    def foreach (f: A => Any @thunk): Unit @thunk = while (cond) (f (g))
  }

  private class CpsIterable [A] (xs: Iterable [A]) extends CpsMonad [A] {
    def foreach (f: Func [A, Any]) = {
      val i = xs.iterator
      while (i.hasNext) (f (i.next))
    }}

  class CpsIterableWrapper [A] private [cps] (xs: Iterable [A]) {
    def cps: CpsMonad [A] = new CpsIterable (xs)
  }

  private class CpsOption [A] (x: Option [A]) extends CpsMonad [A] {
    def foreach (f: Func [A, Any]) = {
      if (x.isDefined) { f (x.get); () } else cut ()
    }}

  class CpsOptionWrapper [A] private [cps] (x: Option [A]) {
    def cps: CpsMonad [A] = new CpsOption (x)
  }}
