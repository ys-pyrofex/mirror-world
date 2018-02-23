package coop.rchain.mirror_world

import cats.implicits._
import coop.rchain.mirror_world.Matcher._
import org.log4s._
import org.scalatest._

import scala.collection.mutable

@SuppressWarnings(
  Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Nothing"))
class StorageActionsTest extends FlatSpec with Matchers with OptionValues with SequentialNestedSuiteExecution with StorageTestHelpers {

  val logger: Logger = getLogger

  override def withFixture(test: NoArgTest): Outcome = {
    logger.debug(s"Test: ${test.name}")
    super.withFixture(test)
  }

  def withTestStorage(f: Store[Channel, Pattern, String, Continuation[String]] => Unit)(implicit sc: Serialize[Channel]): Unit = {
    val store: Store[Channel, Pattern, String, Continuation[String]] = Store.empty
    f(store)
  }

  "produce" should "work" in withTestStorage { store =>
    runKs(produce(store, "hello", "world"))
    store.getAs(List("hello")) shouldBe List("world")
  }

  "produce, consume" should "work" in withTestStorage { store =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(store, "hello", "world")
    val wk2 = consume(store, List("hello"), List(Wildcard), capture(results))

    val test = List(wk1, wk2)

    test.foreach(runKs)

    store.getAs(List("hello")) shouldBe Nil
    results.toList shouldBe List(List("world"))
  }

  "produce, produce" should "work" in withTestStorage { store =>
    val wk1 = produce(store, "hello", "world")
    val wk2 = produce(store, "hello", "goodbye")

    val test = List(wk1, wk2)

    test.foreach(runKs)

    store.getAs(List("hello")) shouldBe List("goodbye", "world")
  }

  "produce, produce, produce, consume" should "work" in withTestStorage { store =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(store, "ch1", "world")
    val wk2 = produce(store, "ch1", "hello")
    val wk3 = produce(store, "ch1", "goodbye")
    val wk4 = consume(store, List("ch1", "ch2", "ch3"), List(Wildcard), capture(results))

    val test = List(wk1, wk2, wk3, wk4)

    test.foreach(runKs)

    store.getAs(List("ch1")) shouldBe List("hello", "world")
    results.toList shouldBe List(List("goodbye"))
  }

  "produce ch1, consume" should "work" in withTestStorage { store =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(store, "ch1", "hello")
    val wk2 = consume(store, List("ch1", "ch2", "ch3"), List(Wildcard), capture(results))

    val test = List(wk1, wk2)

    test.foreach(runKs)

    store.getAs(List("ch1")) shouldBe Nil
    results.toList shouldBe List(List("hello"))
  }

  "produce ch1, produce ch2, produce ch2, consume ch1 ch2 ch3, consume ch2 ch3, consume ch3" should "work" in withTestStorage { store =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(store, "ch1", "hello")
    val wk2 = produce(store, "ch2", "world")
    val wk3 = produce(store, "ch3", "goodbye")
    val wk4 = consume(store, List("ch1", "ch2", "ch3"), List(Wildcard), capture(results))
    val wk5 = consume(store, List("ch2", "ch3"), List(Wildcard), capture(results))
    val wk6 = consume(store, List("ch3"), List(Wildcard), capture(results))

    val test = List(wk1, wk2, wk3, wk4, wk5, wk6)

    test.foreach(runKs)

    store.getAs(List("ch1")) shouldBe Nil
    results.toList shouldBe List(List("hello"), List("world"), List("goodbye"))
  }

  "produce, produce, produce, consume, consume, consume" should "work" in withTestStorage { store =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = produce(store, "ch1", "world")
    val wk2 = produce(store, "ch1", "hello")
    val wk3 = produce(store, "ch1", "goodbye")
    val wk4 = consume(store, List("ch1"), List(Wildcard), capture(results))
    val wk5 = consume(store, List("ch1"), List(Wildcard), capture(results))
    val wk6 = consume(store, List("ch1"), List(Wildcard), capture(results))

    val test = List(wk1, wk2, wk3, wk4, wk5, wk6)

    test.foreach(runKs)

    store.getAs(List("hello")) shouldBe Nil
    results shouldBe List(List("goodbye"), List("hello"), List("world"))
  }

  "consume on multiple channels, produce" should "work" in withTestStorage { store =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1 = consume(store, List("hello", "world"), List(Wildcard), capture(results))
    val wk2 = produce(store, "world", "This is some data")

    val test = List(wk1, wk2)

    test.foreach(runKs)

    store.getAs(List("world")) shouldBe Nil
    results.toList shouldBe List(List("This is some data"))
  }

  "A match experiment" should "work" in withTestStorage { store =>
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1  = consume(store, List("hello", "world"), List(StringMatch("This is some data")), capture(results))
    val wk2  = produce(store, "foo", "This is some data")
    val test = List(wk1, wk2)

    test.foreach(runKs)

    store.getAs(List("hello", "world")) shouldBe Nil
    store.getAs(List("foo")) shouldBe List("This is some data")
  }

  "Another match experiment" should "work" in withTestStorage { store =>
    val results1: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]
    val results2: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1  = consume(store, List("hello"), List(StringMatch("This is some data")), capture(results1))
    val wk2  = consume(store, List("world"), List(StringMatch("This is some other data")), capture(results2))
    val wk3  = produce(store, "hello", "This is some data")
    val wk4  = produce(store, "world", "This is some other data")
    val test = List(wk1, wk2, wk3, wk4)

    test.foreach(runKs)

    store.getAs(List("hello")) shouldBe Nil
    store.getAs(List("world")) shouldBe Nil
    results1.toList shouldBe List(List("This is some data"))
    results2.toList shouldBe List(List("This is some other data"))
  }

  "consume on multiple channels, consume on a same channel, produce" should "work" in withTestStorage { store =>
    val results1: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]
    val results2: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1  = consume(store, List("hello", "goodbye"), List(Wildcard), capture(results1))
    val wk2  = consume(store, List("goodbye"), List(Wildcard), capture(results2))
    val wk3  = produce(store, "hello", "This is some data")
    val wk4  = produce(store, "goodbye", "This is some other data")
    val test = List(wk1, wk2, wk3, wk4)

    test.foreach(runKs)

    store.getAs(List("hello")) shouldBe Nil
    store.getAs(List("goodbye")) shouldBe Nil
    results1.toList shouldBe List(List("This is some data"))
    results2.toList shouldBe List(List("This is some other data"))
  }

  "consume on a channel, consume on same channel, produce" should "work" in withTestStorage { store =>
    val results1: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]
    val results2: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    val wk1  = consume(store, List("hello"), List(Wildcard), capture(results1))
    val wk2  = consume(store, List("hello"), List(StringMatch("This is some data")), capture(results2))
    val wk3  = produce(store, "hello", "This is some data")
    val wk4  = produce(store, "hello", "This is some other data")
    val test = List(wk1, wk2, wk3, wk4)

    test.foreach(runKs)

    results2.toList shouldBe List(List("This is some data"))
    store.getAs(List("hello")) shouldBe List("This is some other data")
  }
}
