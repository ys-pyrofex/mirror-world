package coop.rchain.mirror_world

import cats.implicits._
import coop.rchain.mirror_world.{ignore => ign}
import org.log4s.Logger

import scala.collection.mutable

trait StorageTestHelpers {

  val logger: Logger

  type Channel = String

  type Continuation[A] = (List[A]) => Unit

  def capture[A](res: mutable.ListBuffer[List[A]]): Continuation[A] =
    (as: List[A]) => ign(res += as)

  def runKs(t: Option[(Continuation[String], List[String])]): Unit =
    t match {
      case Some((k, data)) =>
        logger.debug(s"runK: <lambda>($data)")
        k(data)
      case None =>
        ()
    }
}
