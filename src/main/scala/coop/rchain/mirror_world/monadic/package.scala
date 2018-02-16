package coop.rchain.mirror_world

import cats.Id
import cats.data.{ReaderT, Reader}
import cats.implicits._

package object monadic {

  // TODO(ht): better names?

  def mconsume[A, K](channels: Seq[Channel], patterns: Seq[Pattern], k: K): Reader[Storage[A, K], (Seq[(K, Seq[Pattern])], Seq[A])] =
    ReaderT(consume(_, channels, patterns, k).pure[Id])

  def mproduce[A, K](channel: Channel, data: A): Reader[Storage[A, K], (Seq[(K, Seq[Pattern])], Seq[A])] =
    ReaderT(produce(_, channel, data).pure[Id])
}
