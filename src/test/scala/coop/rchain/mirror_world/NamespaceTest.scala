package coop.rchain.mirror_world

import coop.rchain.mirror_world.{ignore => ign}
import org.log4s._
import org.scalatest._

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements"))
class NamespaceTest extends FlatSpec with Matchers with OptionValues with SequentialNestedSuiteExecution {

  private val logger: Logger = getLogger

  override def withFixture(test: NoArgTest): Outcome = {
    logger.debug(s"Test: ${test.name}")
    super.withFixture(test)
  }

  type Continuation[A] = (Seq[A]) => Unit

  def dataAt[A, K](ns: Namespace[A, K], channels: Seq[Channel]): Option[Seq[A]] =
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

  "produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)

    runKs(ns.produce("hello", "world"))

    dataAt(ns, Seq("hello")).value shouldBe Seq("world")
  }

  "produce, consume" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    runKs(ns.produce("hello", "world"))
    runKs(ns.consume(Seq("hello"), Seq(Wildcard), capture(results)))

    dataAt(ns, Seq("hello")).value shouldBe Seq("world")
    results.toList shouldBe Seq(Seq("world"))
  }

  "produce, produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)

    runKs(ns.produce("hello", "world"))
    runKs(ns.produce("hello", "goodbye"))

    dataAt(ns, Seq("hello")).value shouldBe Seq("goodbye", "world")
  }

  "produce, produce, consume" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    runKs(ns.produce("hello", "world"))
    runKs(ns.produce("hello", "hello"))
    runKs(ns.produce("hello", "goodbye"))
    runKs(ns.consume(Seq("hello"), Seq(Wildcard), capture(results)))

    dataAt(ns, Seq("hello")).value shouldBe Seq("goodbye", "hello", "world")
    results.toList shouldBe Seq(Seq("goodbye", "hello", "world"))
  }

  "consume on multiple channels, produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    runKs(ns.consume(Seq("hello", "world"), Seq(Wildcard, Wildcard), capture(results)))
    runKs(ns.produce("world", "This is some data"))

    dataAt(ns, Seq("hello", "world")).value shouldBe Nil
    results.toList shouldBe Seq(Nil, Seq("This is some data"))
  }

  "consume on multiple channels, consume on a same channel, produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results1: mutable.ListBuffer[Seq[String]]   = mutable.ListBuffer.empty[Seq[String]]
    val results2: mutable.ListBuffer[Seq[String]]   = mutable.ListBuffer.empty[Seq[String]]

    runKs(ns.consume(Seq("hello", "goodbye"), Seq(StringMatch("hello"), StringMatch("goodbye")), capture(results1)))
    runKs(ns.consume(Seq("goodbye"), Seq(Wildcard), capture(results2)))
    runKs(ns.produce("goodbye", "This is some data"))

    dataAt(ns, Seq("hello", "goodbye")).value shouldBe Nil
    dataAt(ns, Seq("goodbye")).value shouldBe Nil
    results1.toList shouldBe Seq(Nil, Seq("This is some data"))
    results2.toList shouldBe Seq(Nil, Seq("This is some data"))
  }

  "two consumes on a single channel, produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    runKs(ns.consume(Seq("hello"), Seq(Wildcard), capture(results)))
    runKs(ns.consume(Seq("hello"), Seq(StringMatch("hello")), capture(results)))
    runKs(ns.produce("hello", "This is some data"))
    runKs(ns.produce("hello", "This is some other data"))

    dataAt(ns, Seq("hello")).value shouldBe Nil
    results.toList shouldBe Seq(Nil, Nil, Seq("This is some data"), Seq("This is some other data"))
  }

  "the hello world example" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    def testConsumer(k: Continuation[String])(channels: Seq[String]): Unit = {
      runKs(ns.consume(channels, Seq(Wildcard), k))
    }

    def test(k: Continuation[String]): Unit = {
      runKs(ns.consume(Seq("helloworld"), Seq(Wildcard), k))
      runKs(ns.produce("helloworld", "world"))
      runKs(ns.produce("world", "Hello World"))
    }

    test(testConsumer(capture(results)))

    dataAt(ns, Seq("helloworld")).value shouldBe Nil
    results.toList shouldBe Seq(Nil, Nil, Seq("Hello World"))
  }
}
