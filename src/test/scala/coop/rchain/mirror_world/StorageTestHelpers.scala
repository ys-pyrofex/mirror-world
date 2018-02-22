package coop.rchain.mirror_world

import java.nio.charset.StandardCharsets

import coop.rchain.mirror_world.{ignore => ign}
import org.log4s.Logger

import scala.collection.mutable

import cats.Id
import cats.data.{ReaderT, Reader}
import cats.implicits._

trait StorageTestHelpers {

  val logger: Logger

  type Continuation[A] = (Seq[A]) => Unit

  type Test =
    Reader[Storage[Channel, Pattern, String, Continuation[String]], List[(Seq[(Continuation[String], Seq[Pattern])], Seq[String])]]

  def mconsume(channels: Seq[Channel], patterns: Seq[Pattern], k: Continuation[String])(implicit m: Matcher[Pattern, String])
    : Reader[Storage[Channel, Pattern, String, Continuation[String]], (Seq[(Continuation[String], Seq[Pattern])], Seq[String])] =
    ReaderT(consume[Channel, Pattern, String, Continuation[String]](_, channels, patterns, k).pure[Id])

  def mproduce(channel: Channel, data: String)(implicit m: Matcher[Pattern, String])
    : Reader[Storage[Channel, Pattern, String, Continuation[String]], (Seq[(Continuation[String], Seq[Pattern])], Seq[String])] =
    ReaderT(produce[Channel, Pattern, String, Continuation[String]](_, channel, data).pure[Id])

  implicit object continuationOrdering extends Ordering[Continuation[String]] {
    def compare(x: Continuation[String], y: Continuation[String]): Int = 0
  }

  implicit object stringSerializer extends Serialize[String] {
    def encode(a: String): Array[Byte]     = a.getBytes(StandardCharsets.UTF_8)
    def decode(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)
  }

  def dataAt[P, A, K](ns: Storage[Channel, P, A, K], channels: Seq[Channel]): Seq[A] =
    ns.tuplespace.as(channels)

  def runKs(t: (Seq[(Continuation[String], Seq[Pattern])], Seq[String])): Unit =
    t match {
      case (waitingContinuations, data) =>
        for ((wk, _) <- waitingContinuations) {
          logger.debug(s"runK: <lambda>($data)")
          wk(data)
        }
    }

  def capture[A](res: mutable.ListBuffer[Seq[A]]): Continuation[A] = (as: Seq[A]) => ign(res += as)
}
