package coop.rchain.mirror_world

import coop.rchain.mirror_world.{ignore => ign}
import org.log4s._
import org.scalatest.{FlatSpec, Matchers, OptionValues, Outcome}

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements"))
class HelloWorldTest extends FlatSpec with Matchers with OptionValues {

  private val logger: Logger = getLogger

  override def withFixture(test: NoArgTest): Outcome = {
    logger.debug(s"Test: ${test.name}")
    super.withFixture(test)
  }

  type Continuation[A] = (Seq[A]) => Unit

  def dataAt[A, K](ns: Storage[A, K], channels: Seq[Channel]): Option[Seq[A]] =
    ns.tuplespace.get(channels).map(_.data)

  def runKs(t: (Seq[WaitingContinuation[Continuation[String]]], Seq[String])): Unit =
    t match {
      case (waitingContinuations, data) =>
        for (wk <- waitingContinuations) {
          logger.debug(s"runK: <lambda>($data)")
          wk.k(data)
        }
    }

  def capture[A](res: mutable.ListBuffer[Seq[A]]): Continuation[A] = (as: Seq[A]) => ign(res += as)

  "the hello world example" should "work" in {

    val ns: Storage[String, Continuation[String]] = new Storage(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]  = mutable.ListBuffer.empty[Seq[String]]

    def testConsumer(k: Continuation[String])(channels: Seq[String]): Unit = {
      runKs(consume(ns, channels, Seq(Wildcard), k))
    }

    def test(k: Continuation[String]): Unit = {
      runKs(consume(ns, Seq("helloworld"), Seq(Wildcard), k))
      runKs(produce(ns, "helloworld", "world"))
      runKs(produce(ns, "world", "Hello World"))
    }

    test(testConsumer(capture(results)))

    dataAt(ns, Seq("helloworld")).value shouldBe Nil
    results.toList shouldBe Seq(Nil, Nil, Seq("Hello World"))
  }
}
