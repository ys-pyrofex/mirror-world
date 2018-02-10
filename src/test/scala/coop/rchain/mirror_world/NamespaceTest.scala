package coop.rchain.mirror_world

import coop.rchain.mirror_world.{ignore => ign}
import org.scalatest.{FlatSpec, Matchers, OptionValues}

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements"))
class NamespaceTest extends FlatSpec with Matchers with OptionValues {

  def dataAt[A](ns: Namespace[A], channels: Seq[Channel]): Option[Seq[A]] =
    ns.tupleSpace.get(channels).map(_.data)

  def capture[A](res: mutable.ListBuffer[Seq[A]]): Continuation[A] = (as: Seq[A]) => ign(res += as)

  "produce" should "work" in {

    val ns: Namespace[String] = new Namespace(mutable.Map.empty)

    ns.produce("helloworld", "world")

    dataAt(ns, singleton("helloworld")).value shouldBe Seq("world")
  }

  "produce, consume" should "work" in {

    val ns: Namespace[String]                    = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]] = mutable.ListBuffer.empty[Seq[String]]

    ns.produce("helloworld", "world")
    ns.consume(Seq("helloworld"), Seq(Wildcard), capture(results))

    dataAt(ns, singleton("helloworld")).value shouldBe Seq("world")
    results.toList shouldBe Seq(Seq("world"))
  }

  "produce, produce" should "work" in {

    val ns: Namespace[String] = new Namespace(mutable.Map.empty)

    ns.produce("helloworld", "world")
    ns.produce("helloworld", "quux")

    dataAt(ns, singleton("helloworld")).value shouldBe Seq("quux", "world")
  }

  "produce, produce, consume" should "work" in {

    val ns: Namespace[String]                    = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]] = mutable.ListBuffer.empty[Seq[String]]

    ns.produce("helloworld", "world")
    ns.produce("helloworld", "quux")
    ns.produce("helloworld", "bar")
    ns.produce("helloworld", "baz")
    ns.consume(Seq("helloworld"), Seq(Wildcard), capture(results))

    dataAt(ns, singleton("helloworld")).value shouldBe Seq("baz", "bar", "quux", "world")
    results.toList shouldBe Seq(Seq("baz", "bar", "quux", "world"))
  }

  "consume on multiple channels, produce" should "work" in {

    val ns: Namespace[String]                    = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]] = mutable.ListBuffer.empty[Seq[String]]

    ns.consume(Seq("helloworld", "quux"), Seq(Wildcard, Wildcard), capture(results))
    ns.produce("quux", "Hello World")

    dataAt(ns, Seq("helloworld", "quux")).value shouldBe Nil
    results.toList shouldBe Seq(Seq("Hello World"))
  }

  "consume on multiple channels, consume on a same channel, produce" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results1: mutable.ListBuffer[Seq[String]] = mutable.ListBuffer.empty[Seq[String]]
    val results2: mutable.ListBuffer[Seq[String]] = mutable.ListBuffer.empty[Seq[String]]

    ns.consume(Seq("helloworld", "quux"), Seq(StringMatch("helloworld"), StringMatch("quux")), capture(results1))
    ns.consume(Seq("quux"), Seq(Wildcard), capture(results2))
    ns.produce("quux", "Hello World")

    dataAt(ns, Seq("helloworld", "quux")).value shouldBe Nil
    dataAt(ns, Seq("quux")).value shouldBe Nil
    results1.toList shouldBe Seq(Seq("Hello World"))
    results2.toList shouldBe Seq(Seq("Hello World"))
  }

  "two consumes on a single channel, produce" should "work" in {

    val ns: Namespace[String]                    = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]] = mutable.ListBuffer.empty[Seq[String]]

    ns.consume(Seq("helloworld"), Seq(Wildcard), capture(results))
    ns.consume(Seq("helloworld"), Seq(StringMatch("helloworld")), capture(results))
    ns.produce("helloworld", "Hello World")
    ns.produce("helloworld", "Hello World")

    dataAt(ns, singleton("helloworld")).value shouldBe Nil
    results.toList shouldBe Seq(Seq("Hello World"), Seq("Hello World"))
  }

  "the hello world example" should "work" in {

    val ns: Namespace[String]                    = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]] = mutable.ListBuffer.empty[Seq[String]]

    def testConsumer(k: Continuation[String])(channels: Seq[String]): Unit =
      ns.consume(channels, Seq(Wildcard), k)

    def test(k: Continuation[String]): Unit = {
      ns.consume(Seq("helloworld"), Seq(Wildcard), k)
      ns.produce("helloworld", "world")
      ns.produce("world", "Hello World")
    }

    test(testConsumer(capture(results)))

    dataAt(ns, singleton("helloworld")).value shouldBe Nil
    results.toList shouldBe Seq(Seq("Hello World"))
  }
}
