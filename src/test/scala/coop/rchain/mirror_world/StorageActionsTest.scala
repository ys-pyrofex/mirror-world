package coop.rchain.mirror_world

import cats.data.Reader
import coop.rchain.mirror_world.monadic._
import coop.rchain.mirror_world.{ignore => ign}
import org.log4s._
import org.scalatest._

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements"))
class StorageActionsTest extends FlatSpec with Matchers with OptionValues with SequentialNestedSuiteExecution {

  private val logger: Logger = getLogger

  override def withFixture(test: NoArgTest): Outcome = {
    logger.debug(s"Test: ${test.name}")
    super.withFixture(test)
  }

  type Continuation[A] = (Seq[A]) => Unit

  type Test = Reader[Storage[String, Continuation[String]], List[(Seq[WaitingContinuation[Continuation[String]]], Seq[String])]]

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

  "produce" should "work" in {

    val ns: Storage[String, Continuation[String]] = new Storage(mutable.Map.empty)

    mproduce("hello", "world").map(runKs).run(ns)

    dataAt(ns, Seq("hello")).value shouldBe Seq("world")
  }

  "produce, consume" should "work" in {

    val ns: Storage[String, Continuation[String]] = new Storage(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]  = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mproduce("hello", "world")
      wk2 <- mconsume(Seq("hello"), Seq(Wildcard), capture(results))
    } yield List(wk1, wk2)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello")).value shouldBe Seq("world")
    results.toList shouldBe Seq(Seq("world"))
  }

  "produce, produce" should "work" in {

    val ns: Storage[String, Continuation[String]] = new Storage(mutable.Map.empty)

    val test: Test = for {
      wk1 <- mproduce("hello", "world")
      wk2 <- mproduce("hello", "goodbye")
    } yield List(wk1, wk2)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello")).value shouldBe Seq("goodbye", "world")
  }

  "produce, produce, consume" should "work" in {

    val ns: Storage[String, Continuation[String]] = new Storage(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]  = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mproduce("hello", "world")
      wk2 <- mproduce("hello", "hello")
      wk3 <- mproduce("hello", "goodbye")
      wk4 <- mconsume(Seq("hello"), Seq(Wildcard), capture(results))
    } yield List(wk1, wk2, wk3, wk4)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello")).value shouldBe Seq("goodbye", "hello", "world")
    results.toList shouldBe Seq(Seq("goodbye", "hello", "world"))
  }

  "consume on multiple channels, produce" should "work" in {

    val ns: Storage[String, Continuation[String]] = new Storage(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]  = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mconsume(Seq("hello", "world"), Seq(Wildcard, Wildcard), capture(results))
      wk2 <- mproduce("world", "This is some data")
    } yield List(wk1, wk2)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello", "world")).value shouldBe Nil
    results.toList shouldBe Seq(Nil, Seq("This is some data"))
  }

  "consume on multiple channels, consume on a same channel, produce" should "work" in {

    val ns: Storage[String, Continuation[String]] = new Storage(mutable.Map.empty)
    val results1: mutable.ListBuffer[Seq[String]] = mutable.ListBuffer.empty[Seq[String]]
    val results2: mutable.ListBuffer[Seq[String]] = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mconsume(Seq("hello", "goodbye"), Seq(StringMatch("hello"), StringMatch("goodbye")), capture(results1))
      wk2 <- mconsume(Seq("goodbye"), Seq(Wildcard), capture(results2))
      wk3 <- mproduce("goodbye", "This is some data")
    } yield List(wk1, wk2, wk3)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello", "goodbye")).value shouldBe Nil
    dataAt(ns, Seq("goodbye")).value shouldBe Nil
    results1.toList shouldBe Seq(Nil, Seq("This is some data"))
    results2.toList shouldBe Seq(Nil, Seq("This is some data"))
  }

  "consume on a channel, consume on same channel, produce" should "work" in {

    val ns: Storage[String, Continuation[String]] = new Storage(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]  = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mconsume(Seq("hello"), Seq(Wildcard), capture(results))
      wk2 <- mconsume(Seq("hello"), Seq(StringMatch("hello")), capture(results))
      wk3 <- mproduce("hello", "This is some data")
      wk4 <- mproduce("hello", "This is some other data")
    } yield List(wk1, wk2, wk3, wk4)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello")).value shouldBe Nil
    results.toList shouldBe Seq(Nil, Nil, Seq("This is some data"), Seq("This is some other data"))
  }
}
