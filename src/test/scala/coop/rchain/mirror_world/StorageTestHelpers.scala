package coop.rchain.mirror_world

import java.nio.charset.StandardCharsets

import cats.implicits._
import coop.rchain.mirror_world.{ignore => ign}
import org.log4s.Logger

import scala.collection.mutable

trait StorageTestHelpers {

  val logger: Logger

  def mm[C]: mutable.HashMap[C, mutable.Set[List[C]]] with mutable.MultiMap[C, List[C]] =
    new mutable.HashMap[C, mutable.Set[List[C]]] with mutable.MultiMap[C, List[C]]

  type Continuation[A] = (List[A]) => Unit

  implicit object stringSerializer extends Serialize[String] {
    def encode(a: String): Array[Byte]     = a.getBytes(StandardCharsets.UTF_8)
    def decode(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)
  }

  def dataAt[P, A, K](ns: Store[Channel, P, A, K], channels: List[Channel]): List[A] =
    ns.getAs(channels)

  def runKs(t: Option[(Continuation[String], List[String])]): Unit =
    t match {
      case Some((k, data)) =>
        logger.debug(s"runK: <lambda>($data)")
        k(data)
      case None =>
        ()
    }

  def capture[A](res: mutable.ListBuffer[List[A]]): Continuation[A] = (as: List[A]) => ign(res += as)
}
