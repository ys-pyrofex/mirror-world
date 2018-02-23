package coop.rchain.mirror_world

import coop.rchain.mirror_world.Matcher._
import org.log4s._
import org.scalatest.{FlatSpec, Matchers, OptionValues, Outcome}

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements"))
class HelloWorldTest extends FlatSpec with Matchers with OptionValues with StorageTestHelpers {

  val logger: Logger = getLogger

  override def withFixture(test: NoArgTest): Outcome = {
    logger.debug(s"Test: ${test.name}")
    super.withFixture(test)
  }

  "the hello world example" should "work" in {

    val ns: Store[Channel, Pattern, String, Continuation[String]] = Store.empty
    val results: mutable.ListBuffer[List[String]]                 = mutable.ListBuffer.empty[List[String]]

    def testConsumer(k: Continuation[String])(channels: List[String]): Unit = {
      runKs(consume(ns, channels, List(Wildcard), k))
    }

    def test(k: Continuation[String]): Unit = {
      runKs(consume(ns, List("helloworld"), List(Wildcard), k))
      runKs(produce(ns, "helloworld", "world"))
      runKs(produce(ns, "world", "Hello World"))
    }

    test(testConsumer(capture(results)))

    dataAt(ns, List("helloworld")) shouldBe Nil
    results.toList shouldBe List(List("Hello World"))
  }
}
