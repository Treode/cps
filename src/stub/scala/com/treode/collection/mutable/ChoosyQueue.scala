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

package com.treode.collection.mutable

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/** A queue that allows one to dequeue a particular element, which one can selected by index,
  * predicate or [[scala.util.Random]].  Don't worry, be happy!
  *
  * Algorithmic complexity is based on [[scala.collection.mutable.ResizableArray]].  The standard
  * library does not document the running time of ResizeableArray, and those given below are derived
  * from the following observations of the 2.9.1 implementation: it allocates a backing array with
  * padding, it doubles the size as needed to prevent overflow, and it never reduces the size.
  */
class ChoosyQueue [A] private {
  private [this] val q = ArrayBuffer [A] ()

  /** Tests whether the queue contains no elements.
    * @return `true` if the are no elements in the queue, and `false` otherwise.
    */
  def isEmpty: Boolean = q.isEmpty

  /** Adds an element to the queue. O (1) usually and O (N) in the worst case, where N is the number
    * of elements in the queue.
    *
    * @param v The element to add.
    */
  def enqueue (v: A): Unit = q.append (v)

  /** Removes the element at the given index.  O (1).
    * @param i The index of the element to remove.
    * @return The element at the given index.
    */
  def dequeue (i: Int): A = {
    val v = q (i)
    q (i) = q (q.length - 1)
    q.reduceToSize (q.length - 1)
    v
  }

  /** Removes an element matching the given predicate.  O (N) in the average case, where N is the
    * number of elements in the queue.
    *
    * @param i The predicate to select the element.
    * @return An element which satisfies the predicate, or None if no element does.
    */
  def dequeue (p: A => Boolean): Option [A] = {
    val i = q.indexWhere (p)
    if (i == -1) None else Some (dequeue (i))
  }

  /** Removes a random element. O (1).
    * @param i The random generator to supply a random index.
    * @return The element at the random index.
    */
  def dequeue (random: Random): A = dequeue (random.nextInt (q.length))

  override def toString: String = q.toString
}

object ChoosyQueue {

  def apply [A] () = new ChoosyQueue [A]
}
