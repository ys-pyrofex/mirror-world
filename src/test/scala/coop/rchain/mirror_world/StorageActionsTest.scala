package coop.rchain.mirror_world

import cats.implicits._
import coop.rchain.mirror_world.Matcher._
import org.log4s._
import org.scalatest._

import scala.collection.mutable

@SuppressWarnings(
  Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Nothing"))
class StorageActionsTest
    extends FlatSpec
    with Matchers
    with OptionValues
    with SequentialNestedSuiteExecution
    with StorageTestHelpers
    with StorageFixtures {

  val logger: Logger = getLogger

  override def withFixture(test: NoArgTest): Outcome = {
    logger.debug(s"Test: ${test.name}")
    super.withFixture(test)
  }

  val withStorage: (Storage[Channel, Pattern, String, Continuation[String]] => Unit) => Unit =
    withTestStorage[Channel, Pattern, String, Continuation[String]]

  "produce" should "work" in withStorage { ns =>
    runKs(produce(ns, "hello", "world"))
    dataAt(ns, List("hello")) shouldBe List("world")
  }

  "produce, consume" should "work" in withStorage { ns =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(ns, "hello", "world")
    val wk2 = consume(ns, List("hello"), List(Wildcard), capture(results))

    val test = List(wk1, wk2)

    test.foreach(runKs)

    dataAt(ns, List("hello")) shouldBe Nil
    results.toList shouldBe List(List("world"))
  }

  "produce, produce" should "work" in withStorage { ns =>
    val wk1 = produce(ns, "hello", "world")
    val wk2 = produce(ns, "hello", "goodbye")

    val test = List(wk1, wk2)

    test.foreach(runKs)

    dataAt(ns, List("hello")) shouldBe List("goodbye", "world")
  }

  "produce, produce, produce, consume" should "work" in withStorage { ns =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(ns, "ch1", "world")
    val wk2 = produce(ns, "ch1", "hello")
    val wk3 = produce(ns, "ch1", "goodbye")
    val wk4 = consume(ns, List("ch1", "ch2", "ch3"), List(Wildcard), capture(results))

    val test = List(wk1, wk2, wk3, wk4)

    test.foreach(runKs)

    dataAt(ns, List("ch1")) shouldBe List("hello", "world")
    results.toList shouldBe List(List("goodbye"))
  }

  "produce ch1, consume" should "work" in withStorage { ns =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(ns, "ch1", "hello")
    val wk2 = consume(ns, List("ch1", "ch2", "ch3"), List(Wildcard), capture(results))

    val test = List(wk1, wk2)

    test.foreach(runKs)

    dataAt(ns, List("ch1")) shouldBe Nil
    results.toList shouldBe List(List("hello"))
  }

  "produce ch1, produce ch2, produce ch2, consume ch1 ch2 ch3, consume ch2 ch3, consume ch3" should "work" in withStorage { ns =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(ns, "ch1", "hello")
    val wk2 = produce(ns, "ch2", "world")
    val wk3 = produce(ns, "ch3", "goodbye")
    val wk4 = consume(ns, List("ch1", "ch2", "ch3"), List(Wildcard), capture(results))
    val wk5 = consume(ns, List("ch2", "ch3"), List(Wildcard), capture(results))
    val wk6 = consume(ns, List("ch3"), List(Wildcard), capture(results))

    val test = List(wk1, wk2, wk3, wk4, wk5, wk6)
    println(test)

    test.foreach(runKs)

    dataAt(ns, List("ch1")) shouldBe Nil
    results.toList shouldBe List(List("hello"), List("world"), List("goodbye"))
  }

  "produce, produce, produce, consume, consume, consume" should "work" in withStorage { ns =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(ns, "ch1", "world")
    val wk2 = produce(ns, "ch1", "hello")
    val wk3 = produce(ns, "ch1", "goodbye")
    val wk4 = consume(ns, List("ch1"), List(Wildcard), capture(results))
    val wk5 = consume(ns, List("ch1"), List(Wildcard), capture(results))
    val wk6 = consume(ns, List("ch1"), List(Wildcard), capture(results))

    val test = List(wk1, wk2, wk3, wk4, wk5, wk6)

    test.foreach(runKs)
    println(test)

    dataAt(ns, List("hello")) shouldBe Nil
    results shouldBe List(List("goodbye"), List("hello"), List("world"))
  }

  "consume on multiple channels, produce" should "work" in withStorage { ns =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = consume(ns, List("hello", "world"), List(Wildcard), capture(results))
    val wk2 = produce(ns, "world", "This is some data")

    val test = List(wk1, wk2)

    test.foreach(runKs)

    dataAt(ns, List("world")) shouldBe Nil
    results.toList shouldBe List(List("This is some data"))
  }

  "A match experiment" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty, mm[Channel])
    val results: mutable.ListBuffer[List[String]]                   = mutable.ListBuffer.empty[List[String]]

    val wk1  = consume(ns, List("hello", "world"), List(StringMatch("This is some data")), capture(results))
    val wk2  = produce(ns, "foo", "This is some data")
    val test = List(wk1, wk2)

    test.foreach(runKs)

    dataAt(ns, List("hello", "world")) shouldBe Nil
    dataAt(ns, List("foo")) shouldBe List("This is some data")
  }

  "Another match experiment" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty, mm[Channel])
    val results1: mutable.ListBuffer[List[String]]                  = mutable.ListBuffer.empty[List[String]]
    val results2: mutable.ListBuffer[List[String]]                  = mutable.ListBuffer.empty[List[String]]

    val wk1  = consume(ns, List("hello"), List(StringMatch("This is some data")), capture(results1))
    val wk2  = consume(ns, List("world"), List(StringMatch("This is some other data")), capture(results2))
    val wk3  = produce(ns, "hello", "This is some data")
    val wk4  = produce(ns, "world", "This is some other data")
    val test = List(wk1, wk2, wk3, wk4)

    test.foreach(runKs)

    dataAt(ns, List("hello")) shouldBe Nil
    dataAt(ns, List("world")) shouldBe Nil
    results1.toList shouldBe List(List("This is some data"))
    results2.toList shouldBe List(List("This is some other data"))
  }

  "consume on multiple channels, consume on a same channel, produce" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty, mm[Channel])
    val results1: mutable.ListBuffer[List[String]]                  = mutable.ListBuffer.empty[List[String]]
    val results2: mutable.ListBuffer[List[String]]                  = mutable.ListBuffer.empty[List[String]]

    val wk1  = consume(ns, List("hello", "goodbye"), List(Wildcard), capture(results1))
    val wk2  = consume(ns, List("goodbye"), List(Wildcard), capture(results2))
    val wk3  = produce(ns, "hello", "This is some data")
    val wk4  = produce(ns, "goodbye", "This is some other data")
    val test = List(wk1, wk2, wk3, wk4)

    test.foreach(runKs)

    dataAt(ns, List("hello")) shouldBe Nil
    dataAt(ns, List("goodbye")) shouldBe Nil
    results1.toList shouldBe List(List("This is some data"))
    results2.toList shouldBe List(List("This is some other data"))
  }

  "consume on a channel, consume on same channel, produce" should "work" in {

    val ns: Storage[Channel, Pattern, String, Continuation[String]] = new Storage(Store.empty, mm[Channel])
    val results1: mutable.ListBuffer[List[String]]                  = mutable.ListBuffer.empty[List[String]]
    val results2: mutable.ListBuffer[List[String]]                  = mutable.ListBuffer.empty[List[String]]

    val wk1  = consume(ns, List("hello"), List(Wildcard), capture(results1))
    val wk2  = consume(ns, List("hello"), List(StringMatch("This is some data")), capture(results2))
    val wk3  = produce(ns, "hello", "This is some data")
    val wk4  = produce(ns, "hello", "This is some other data")
    val test = List(wk1, wk2, wk3, wk4)

    test.foreach(runKs)

    results2.toList shouldBe List(List("This is some data"))
    dataAt(ns, List("hello")) shouldBe List("This is some other data")
  }
}
