package coop.rchain

import scala.collection.mutable

package object mirror_world {

  type Channel         = String
  type Continuation[A] = (List[A]) => Unit
  type Tuplespace[A]   = mutable.Map[List[Channel], Subspace[A]]

  def ignore[A](a: => A): Unit = {
    val _: A = a
    ()
  }

  def singleton[A](a: A): List[A] =
    List(a)
}
