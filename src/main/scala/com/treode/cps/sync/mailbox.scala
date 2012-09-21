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
package sync

import scala.annotation.tailrec
import com.treode.cps.scheduler.Scheduler

/** A first-in first-out message queue. */
trait Maildrop [-A] {

  /** Queue a message. */
  def send (m: A): Unit

  /** Convenient syntax for `send`.  This allows syntax like
    * {{{ mailbox ! message }}}
    */
  def ! (m: A): Unit = send (m)
}

object Maildrop {

  /** Convert a function into a Maildrop. */
  def apply [A] (f: A => Any): Maildrop [A] =
    new Maildrop [A] {
      def send (m: A) = f (m)
    }}

/** A first-in first-out message queue. */
trait Mailbox [A] extends Maildrop [A] {

  /** Get a message, or wait for one. */
  def receive (): A @thunk

  /** Get all messages, or wait for the next one. */
  def receiveAll (): List [A] @thunk

  /** Convenient syntax for `receive`. */
  def open [B] (f: A => B): B @thunk = {
    val m = receive ()
    f (m)
  }

  /** Convenient syntax for `receive`. */
  def openS [B] (f: A => B @thunk): B @thunk = {
    val m = receive ()
    f (m)
  }

  /** Process messages indefinitely; spawns the loop, and returns immediately. */
  def loop (f: A => Any): Unit

  /** Process messages indefinitely; spawns the loop, and returns immediately. */
  def loopS (f: A => Any @thunk): Unit
}

private class MailboxImpl [A] (protected [this] val scheduler: Scheduler)
extends AtomicStateWithSuspendable with Mailbox [A] {
  import scheduler.spawn

  private type Msgs = List [A]
  private type Rcvrs = List [A => Unit]

  initialize (Empty)

  @tailrec
  private def _zip [A, B] (xs: List [A], ys: List [B], xys: List [(A, B)]): (List [(A, B)], List [A], List [B]) =
    (xs, ys) match {
      case (x::xs, y::ys) => _zip (xs, ys, (x,y)::xys)
      case _ => (xys, xs, ys)
    }

  /** Like standard zip but also returns the tails, one of which will be empty. */
  private def zip [A, B] (xs: List [A], ys: List [B]) = _zip (xs, ys, List ())

  protected [this] trait State {

    def send (m: A): Behavior [Unit]

    def receive (k: A => Unit): Behavior [Unit]

    def receiveAll (k: List [A] => Unit): Behavior [Unit]

    def reversed (mOut: Msgs, kOut: Rcvrs): Behavior [Unit] =
      illegalState ("Cannot reverse when not reversing.")

    def zipped (mOut: Msgs, kOut: Rcvrs): Behavior [Unit] =
      illegalState ("Cannot zip when not zipping.")
  }

  /** No messages or receivers waiting. */
  private [this] object Empty extends State {

    def send (m: A) =
      moveTo (new MsgsQd (m)) withoutEffect

    def receive (k: A => Unit) =
      moveTo (new RcvrsQd (k)) withoutEffect

    def receiveAll (k: List [A] => Unit) =
      receive (m => k (List (m)))
  }

  /** Only messages waiting; queue a new message; invoke a receiver immediately. */
  private [this] class MsgsQd (in: Msgs, out: Msgs) extends State {
    def this (m: A) = this (List (), List (m))

    def send (m: A) =
      moveTo (new MsgsQd (m::in, out)) withoutEffect

    def receive (k: A => Unit) =
      (in, out) match {
        // When in state MsgsQd, one or both of in and out should have messages.
        case (List (), List ()) =>
          assertionError [Unit] ("Message queue should not be empty.")

        // Return the last waiting message, and move to the state Empty.
        case (List (), List (m)) =>
          moveTo (Empty) withEffect (k (m))

        // Return the next waiting message, and stay in this state.
        case (_, m::ms) =>
          moveTo (new MsgsQd (in, ms)) withEffect (k (m))

        // Reverse the messages waiting on the in queue.  If this fiber successfully moves the
        // mailbox to the Reversing state, then give the reversed `in` queue to the `reversed`
        // transition.
        case (_, List ()) =>
          moveTo (new Reversing) withEffect {
            MailboxImpl.this.reversed (in.reverse, List (k))
          }}

    def receiveAll (k: List [A] => Unit) =
      moveTo (Empty) withEffect (k (out reverse_::: in))
  }

  /** Only receivers waiting; queue a new receiver; given a message invoke a receiver immediately. */
  private [this] class RcvrsQd (in: Rcvrs, out: Rcvrs) extends State {
    def this (k: A => Unit) = this (List (), List (k))

    def send (m: A) =
      (in, out) match {
        // When in state RcvrsQd, one or both of in and out should have receivers.
        case (List (), List ()) =>
          assertionError [Unit] ("Receiver queue should not be empty.")

        // Give the message to the last waiting receiver, and move to the state Empty.
        case (List (), List (k)) =>
          moveTo (Empty) withEffect (k (m))

        // Give the message to the next waiting receiver, and stay in this state.
        case (_, k::ks) =>
          moveTo (new RcvrsQd (in, ks)) withEffect (k (m))

        // Reverse the receivers waiting on the in queue.
        case (_, List ()) =>
          moveTo (new Reversing) withEffect {
            MailboxImpl.this.reversed (List (m), in.reverse)
          }}

    def receive (k: A => Unit) =
      moveTo (new RcvrsQd (k::in, out)) withoutEffect

    def receiveAll (k: List [A] => Unit) =
      receive (m => k (List (m)))
  }

  /** Some fiber is reversing the `in` lists; queue a new message or receiver. */
  private [this] class Reversing private (mIn: Msgs, kIn: Rcvrs) extends State {
    def this () = this (List (), List ())

    def send (m: A) =
      moveTo (new Reversing (m::mIn, kIn)) withoutEffect

    def receive (k: A => Unit) =
      moveTo (new Reversing (mIn, k::kIn)) withoutEffect

    def receiveAll (k: List [A] => Unit) =
      receive (m => k (List (m)))

    /** The fiber finished reversing the lists, begin matching messages to receivers.  If this
      * fiber successfully moves to the Zipping state, then it zips waiting messages with waiting
      * receivers, that is it returns the waiting message to the waiting receiver.  When it's done,
      * it hands the left over messages or receivers to the `zipped` transition.
      */
    override def reversed (mOut: Msgs, kOut: Rcvrs) =
      moveTo (new Zipping (mIn, kIn)) withEffect {
        val (mks, ms, ks) = zip (mOut, kOut)
        mks foreach { case (m, k) => k (m) }
        MailboxImpl.this.zipped (ms, ks)
      }}

  /** Some thread is matching waiting messages and receivers; queue a new message or receiver. */
  private [this] class Zipping (mIn: Msgs, kIn: Rcvrs) extends State {

    def send (m: A) =
      moveTo (new Zipping (m::mIn, kIn)) withoutEffect

    def receive (k: A => Unit) =
      moveTo (new Zipping (mIn, k::kIn)) withoutEffect

    def receiveAll (k: List [A] => Unit) =
      receive (m => k (List (m)))

    /** The fiber finished matching messages to receivers; decide what to do next.  At this
      * point we can talk about the messages and receivers that WERE waiting when the mailbox
      * `reversed` in the Reversing state, and we can talk about the messages and receivers that
      * ARE waiting because they queued up while `reversed` did its work.
      */
    override def zipped (mOut: Msgs, kOut: Rcvrs) =
      (mIn, mOut, kIn, kOut) match {

        // All messages and receivers that were waiting paired, and no new messages or receivers
        // arrived during the process.  This mailbox is now Empty.
        case (List (), List (), List (), List ()) =>
          moveTo (Empty) withoutEffect

        // All messages that were waiting went to a receiver and no new messages arrived, but
        // there are still receivers waiting.
        case (List (), List (), _, _) =>
          moveTo (new RcvrsQd (kIn, kOut)) withoutEffect

        // All receivers that were waiting got a message and no new receivers arrived, but there
        // are still messages waiting.
        case (_, _, List (), List ()) =>
          moveTo (new MsgsQd (mIn, mOut)) withoutEffect

        // Anything not the above: maybe the waiting messages and receivers didn't pair up and
        // one or the other was left over, or maybe messages and receivers arrived while the
        // pairing was occurring, or maybe some combination of these things.  Go back to the state
        // Reversing to sort it out.
        case (_, _, _, _) =>
          moveTo (new Reversing) withEffect {
            MailboxImpl.this.reversed (mOut reverse_::: mIn, kOut reverse_::: kIn)
          }}}

  private [this] def reversed (mOut: Msgs, kOut: Rcvrs) = delegate (_.reversed (mOut, kOut))

  private [this] def zipped (mOut: Msgs, kOut: Rcvrs) = delegate (_.zipped (mOut, kOut))

  def send (m: A): Unit = delegate (_.send (m))

  def receive (): A @thunk = delegateT (_.receive)

  def receiveAll (): List [A] @thunk = delegateT (_.receiveAll)

  def loop (f: A => Any): Unit = spawn (while (true) f (receive ()))

  def loopS (f: A => Any @thunk): Unit = spawn (while (true) f (receive ()))

  override def toString = "Mailbox:" + System.identityHashCode (this).toHexString
}

object Mailbox {

  /** Create a mailbox.
    *
    * @param s The scheduler to suspend receivers and spawn loopers.
    */
  def apply [A] (s: Scheduler): Mailbox [A] = new MailboxImpl (s)
}
