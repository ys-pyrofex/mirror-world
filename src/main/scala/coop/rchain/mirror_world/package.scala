package coop.rchain

import scala.collection.mutable

package object mirror_world {

  type Channel         = String
  type Continuation[A] = (Seq[A]) => Unit
  type Tuplespace[A]   = mutable.Map[Seq[Channel], Subspace[A]]

  def ignore[A](a: => A): Unit = {
    val _: A = a
    ()
  }

  def singleton[A](a: A): Seq[A] =
    Seq(a)
}
