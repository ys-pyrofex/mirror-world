package coop.rchain.mirror_world

import coop.rchain.mirror_world.{ignore => ign}
import org.scalatest.{FlatSpec, Matchers, OptionValues}

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements"))
class NamespaceTest extends FlatSpec with Matchers with OptionValues {

  def dataAt[A](ns: Namespace[A], channels: List[Channel]): Option[List[A]] =
    ns.tupleSpace.get(channels).map(_.data)

  def capture[A](res: mutable.ListBuffer[List[A]]): Continuation[A] = (as: List[A]) => ign(res += as)

  "produce" should "work" in {

    val ns: Namespace[String] = new Namespace(mutable.Map.empty)

    ns.produce("helloworld", "world")

    dataAt(ns, singleton("helloworld")).value shouldBe List("world")
  }

  "produce, consume" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.produce("helloworld", "world")
    ns.consume(List("helloworld"), List(Wildcard), capture(results))

    dataAt(ns, singleton("helloworld")).value shouldBe List("world")
    results.toList shouldBe List(List("world"))
  }

  "produce, produce" should "work" in {

    val ns: Namespace[String] = new Namespace(mutable.Map.empty)

    ns.produce("helloworld", "world")
    ns.produce("helloworld", "quux")

    dataAt(ns, singleton("helloworld")).value shouldBe List("quux", "world")
  }

  "produce, produce, consume" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.produce("helloworld", "world")
    ns.produce("helloworld", "quux")
    ns.produce("helloworld", "bar")
    ns.produce("helloworld", "baz")
    ns.consume(List("helloworld"), List(Wildcard), capture(results))

    dataAt(ns, singleton("helloworld")).value shouldBe List("baz", "bar", "quux", "world")
    results.toList shouldBe List(List("baz", "bar", "quux", "world"))
  }

  "consume on multiple channels, produce" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.consume(List("helloworld", "quux"), List(Wildcard, Wildcard), capture(results))
    ns.produce("quux", "Hello World")

    dataAt(ns, List("helloworld", "quux")).value shouldBe List()
    results.toList shouldBe List(List("Hello World"))
  }

  "consume on multiple channels, consume on a same channel, produce" should "work" in {

    val ns: Namespace[String]                      = new Namespace(mutable.Map.empty)
    val results1: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]
    val results2: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.consume(List("helloworld", "quux"), List(StringMatch("helloworld"), StringMatch("quux")), capture(results1))
    ns.consume(List("quux"), List(Wildcard), capture(results2))
    ns.produce("quux", "Hello World")

    dataAt(ns, List("helloworld", "quux")).value shouldBe List()
    dataAt(ns, List("quux")).value shouldBe List()
    results1.toList shouldBe List(List("Hello World"))
    results2.toList shouldBe List(List("Hello World"))
  }

  "two consumes on a single channel, produce" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.consume(List("helloworld"), List(Wildcard), capture(results))
    ns.consume(List("helloworld"), List(StringMatch("helloworld")), capture(results))
    ns.produce("helloworld", "Hello World")
    ns.produce("helloworld", "Hello World")

    dataAt(ns, singleton("helloworld")).value shouldBe List()
    results.toList shouldBe List(List("Hello World"), List("Hello World"))
  }

  "the hello world example" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    def testConsumer(k: Continuation[String])(channels: List[String]): Unit =
      ns.consume(channels, List(Wildcard), k)

    def test(k: Continuation[String]): Unit = {
      ns.consume(List("helloworld"), List(Wildcard), k)
      ns.produce("helloworld", "world")
      ns.produce("world", "Hello World")
    }

    test(testConsumer(capture(results)))

    dataAt(ns, singleton("helloworld")).value shouldBe List()
    results.toList shouldBe List(List("Hello World"))
  }
}
