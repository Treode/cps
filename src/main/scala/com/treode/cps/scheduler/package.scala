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

import scala.util.control.ControlThrowable

package object scheduler {

  private [scheduler] def execute (s: Scheduler, k: () => Any): Unit =
    try {
      k()
    } catch {
      case e: ControlThrowable => throw e
      case e: Throwable =>
        try {
          s.handleUncaughtException (e)
        } catch {
          case e: ControlThrowable => throw e
          case _ => ()
        }}

  private [scheduler] def catcher [A] (s: Scheduler, k: Either [Throwable, A] => Any, v: => A): Unit =
    s.spawn {
      try {
        k (Right (v))
      } catch {
        case e: ControlThrowable => throw e
        case e: Throwable => k (Left (e))
      }}

  private [scheduler] def catcherS [A] (s: Scheduler, k: Either [Throwable, A] => Any, v: => A @thunk): Unit =
    s.spawn {
      try {
        k (Right (v))
      } catch {
        case e: ControlThrowable => throw e
        case e: Throwable => k (Left (e))
      }}

  private [scheduler] def catcherK [A] (k: => A @thunk): Either [Throwable, A] @thunk = {
    try {
      Right [Throwable, A] (k)
    } catch {
    case e: Throwable => cut(); Left (e)
  }}}
