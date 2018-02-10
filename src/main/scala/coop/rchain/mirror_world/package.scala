package coop.rchain

import cats.implicits._

import scala.collection.mutable

package object mirror_world {

  type Persistent          = Boolean
  type Channel             = String
  type ProduceCandidate    = (Channel, Int)
  type ConsumeCandidate[A] = (A, Int)
  type Code[A]             = (Env[A], List[A]) => Unit
  type Env[A]              = Map[String, A]
  type Tuplespace[A]       = mutable.Map[List[Channel], Subspace[A]]

  /** Drops the 'i'th element of a list.
    */
  def dropIndex[T](xs: List[T], n: Int): List[T] = {
    val (l1, l2) = xs splitAt n
    l1 ::: (l2 drop 1)
  }

  /** Extract the nth element of a list and return it and the remainder.
    */
  def extractIndex[T](xs: List[T], n: Int): (T, List[T]) =
    (xs(n), dropIndex(xs, n))

  def ignore[A](a: => A): Unit = {
    val _: A = a
    ()
  }

  def reachAroundGet[A](xs: List[A], i: Int): Option[A] =
    if (i === -1)
      xs.lastOption
    else
      xs.lift(i)
}
