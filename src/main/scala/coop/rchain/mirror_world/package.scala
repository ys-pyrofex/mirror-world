package coop.rchain

import scala.collection.mutable

package object mirror_world {

  type Channel          = String
  type Tuplespace[A, K] = mutable.Map[Seq[Channel], Subspace[A, K]]

  def ignore[A](a: => A): Unit = {
    val _: A = a
    ()
  }
}
