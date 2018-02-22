package coop.rchain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

package object mirror_world extends StorageActions {

  type Channel = String

  def ignore[A](a: => A): Unit = {
    val _: A = a
    ()
  }

  def hashBytes(bs: Array[Byte]): Array[Byte] =
    MessageDigest.getInstance("SHA-256").digest(bs)

  def hashString(s: String): Array[Byte] =
    hashBytes(s.getBytes(StandardCharsets.UTF_8))


  /** Drops the 'i'th element of a list.
    */
  def dropIndex[T](xs: Seq[T], n: Int): Seq[T] = {
    val (l1, l2) = xs splitAt n
    l1 ++ (l2 drop 1)
  }

  /** Extract the nth element of a list and return it and the remainder.
    */
  def extractIndex[T](xs: Seq[T], n: Int): (T, Seq[T]) =
    (xs(n), dropIndex(xs, n))
}
