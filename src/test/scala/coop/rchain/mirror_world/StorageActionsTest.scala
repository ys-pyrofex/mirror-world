package coop.rchain.mirror_world

import cats.implicits._
import coop.rchain.mirror_world.Matcher._
import org.log4s._
import org.scalatest._

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements"))
class StorageActionsTest extends FlatSpec with Matchers with OptionValues with SequentialNestedSuiteExecution with StorageTestHelpers {

  val logger: Logger = getLogger

  override def withFixture(test: NoArgTest): Outcome = {
    logger.debug(s"Test: ${test.name}")
    super.withFixture(test)
  }

  "produce" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty)

    mproduce("hello", "world").map(runKs).run(ns)

    dataAt(ns, Seq("hello")) shouldBe Seq("world")
  }

  "produce, consume" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty)
    val results: mutable.ListBuffer[Seq[String]]                   = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mproduce("hello", "world")
      wk2 <- mconsume(Seq("hello"), Seq[Pattern](Wildcard), capture(results))
    } yield List(wk1, wk2)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello")) shouldBe Seq("world")
    results.toList shouldBe Seq(Seq("world"))
  }

  "produce, produce" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty)

    val test: Test = for {
      wk1 <- mproduce("hello", "world")
      wk2 <- mproduce("hello", "goodbye")
    } yield List(wk1, wk2)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello")) shouldBe Seq("goodbye", "world")
  }

  "produce, produce, consume" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty)
    val results: mutable.ListBuffer[Seq[String]]                   = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mproduce("hello", "world")
      wk2 <- mproduce("hello", "hello")
      wk3 <- mproduce("hello", "goodbye")
      wk4 <- mconsume(Seq("hello"), Seq[Pattern](Wildcard), capture(results))
    } yield List(wk1, wk2, wk3, wk4)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello")) shouldBe Seq("goodbye", "hello", "world")
    results.toList shouldBe Seq(Seq("goodbye", "hello", "world"))
  }

  "consume on multiple channels, produce" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty)
    val results: mutable.ListBuffer[Seq[String]]                   = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mconsume(Seq("hello", "world"), Seq[Pattern](Wildcard, Wildcard), capture(results))
      wk2 <- mproduce("world", "This is some data")
    } yield List(wk1, wk2)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello", "world")) shouldBe Nil
    dataAt(ns, Seq("world")) shouldBe Nil
    results.toList shouldBe Seq(Nil, Seq("This is some data"))
  }

  "A match experiment" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty)
    val results: mutable.ListBuffer[Seq[String]]                   = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mconsume(Seq("hello", "world"), Seq[Pattern](StringMatch("This is some data")), capture(results))
      wk2 <- mproduce("foo", "This is some data")
    } yield List(wk1, wk2)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello", "world")) shouldBe Nil
    dataAt(ns, Seq("foo")) shouldBe Nil
    results.toList shouldBe Seq(Nil, Seq("This is some data"))
  }

  "Another match experiment" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty)
    val results1: mutable.ListBuffer[Seq[String]]                  = mutable.ListBuffer.empty[Seq[String]]
    val results2: mutable.ListBuffer[Seq[String]]                  = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mconsume(Seq("hello", "world"), Seq[Pattern](StringMatch("This is some data")), capture(results1))
      wk2 <- mconsume(Seq("hello", "world"), Seq[Pattern](StringMatch("This is some other data")), capture(results2))
      wk3 <- mproduce("bar", "This is some data")
      wk4 <- mproduce("zaz", "This is some other data")
    } yield List(wk1, wk2, wk3, wk4)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello", "world")) shouldBe Nil
    dataAt(ns, Seq("bar")) shouldBe Nil
    dataAt(ns, Seq("zaz")) shouldBe Nil
    results1.toList shouldBe Seq(Nil, Seq("This is some data"))
    results2.toList shouldBe Seq(Nil, Seq("This is some other data"))
  }

  "consume on multiple channels, consume on a same channel, produce" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty)
    val results1: mutable.ListBuffer[Seq[String]]                  = mutable.ListBuffer.empty[Seq[String]]
    val results2: mutable.ListBuffer[Seq[String]]                  = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mconsume(Seq("hello", "goodbye"), Seq[Pattern](Wildcard), capture(results1))
      wk2 <- mconsume(Seq("goodbye"), Seq[Pattern](Wildcard), capture(results2))
      wk3 <- mproduce("goodbye", "This is some data")
    } yield List(wk1, wk2, wk3)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello", "goodbye")) shouldBe Nil
    dataAt(ns, Seq("goodbye")) shouldBe Nil
    results1.toList shouldBe Seq(Nil, Seq("This is some data"))
    results2.toList shouldBe Seq(Nil, Seq("This is some data"))
  }

  "consume on a channel, consume on same channel, produce" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty)
    val results1: mutable.ListBuffer[Seq[String]]                  = mutable.ListBuffer.empty[Seq[String]]
    val results2: mutable.ListBuffer[Seq[String]]                  = mutable.ListBuffer.empty[Seq[String]]

    val test: Test = for {
      wk1 <- mconsume(Seq("hello"), Seq[Pattern](Wildcard), capture(results1))
      wk2 <- mconsume(Seq("hello"), Seq[Pattern](StringMatch("This is some data")), capture(results2))
      wk3 <- mproduce("hello", "This is some data")
      wk4 <- mproduce("hello", "This is some other data")
    } yield List(wk1, wk2, wk3, wk4)

    test.run(ns).foreach(runKs)

    dataAt(ns, Seq("hello")) shouldBe List("This is some other data")
    results1.toList shouldBe Seq(Nil, Seq("This is some data"))
    results2.toList shouldBe Seq(Nil, Seq("This is some data"))
  }
}
