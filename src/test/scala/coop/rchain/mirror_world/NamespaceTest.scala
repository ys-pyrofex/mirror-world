package coop.rchain.mirror_world

import coop.rchain.mirror_world.{ignore => ign}
import org.scalatest.{FlatSpec, Matchers, OptionValues}

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements"))
class NamespaceTest extends FlatSpec with Matchers with OptionValues {

  type Continuation[A] = (Seq[A]) => Unit

  def dataAt[A, K](ns: Namespace[A, K], channels: Seq[Channel]): Option[Seq[A]] =
    ns.tuplespace.get(channels).map(_.data)

  def capture[A](res: mutable.ListBuffer[Seq[A]]): Continuation[A] = (as: Seq[A]) => ign(res += as)

  "produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)

    ns.produce("hello", "world")

    dataAt(ns, Seq("hello")).value shouldBe Seq("world")
  }

  "produce, consume" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    ns.produce("hello", "world")
    val (wk, product) = ns.consume(Seq("hello"), Seq(Wildcard), capture(results))
    wk.k(product)

    dataAt(ns, Seq("hello")).value shouldBe Seq("world")
    results.toList shouldBe Seq(Seq("world"))
  }

  "produce, produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)

    ns.produce("hello", "world")
    ns.produce("hello", "goodbye")

    dataAt(ns, Seq("hello")).value shouldBe Seq("goodbye", "world")
  }

  "produce, produce, consume" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    ns.produce("hello", "world")
    ns.produce("hello", "hello")
    ns.produce("hello", "goodbye")
    val (wk, product) = ns.consume(Seq("hello"), Seq(Wildcard), capture(results))
    wk.k(product)

    dataAt(ns, Seq("hello")).value shouldBe Seq("goodbye", "hello", "world")
    results.toList shouldBe Seq(Seq("goodbye", "hello", "world"))
  }

  "consume on multiple channels, produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    ns.consume(Seq("hello", "world"), Seq(Wildcard, Wildcard), capture(results))
    val (wks, product) = ns.produce("world", "This is some data")
    for (k <- wks.map(_.k)) k(product)

    dataAt(ns, Seq("hello", "world")).value shouldBe Nil
    results.toList shouldBe Seq(Seq("This is some data"))
  }

  "consume on multiple channels, consume on a same channel, produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results1: mutable.ListBuffer[Seq[String]]   = mutable.ListBuffer.empty[Seq[String]]
    val results2: mutable.ListBuffer[Seq[String]]   = mutable.ListBuffer.empty[Seq[String]]

    ns.consume(Seq("hello", "goodbye"), Seq(StringMatch("hello"), StringMatch("goodbye")), capture(results1))
    ns.consume(Seq("goodbye"), Seq(Wildcard), capture(results2))
    val (wks, product) = ns.produce("goodbye", "This is some data")
    for (k <- wks.map(_.k)) k(product)

    dataAt(ns, Seq("hello", "goodbye")).value shouldBe Nil
    dataAt(ns, Seq("goodbye")).value shouldBe Nil
    results1.toList shouldBe Seq(Seq("This is some data"))
    results2.toList shouldBe Seq(Seq("This is some data"))
  }

  "two consumes on a single channel, produce" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    ns.consume(Seq("hello"), Seq(Wildcard), capture(results))
    ns.consume(Seq("hello"), Seq(StringMatch("hello")), capture(results))
    val (wks1, product1) = ns.produce("hello", "This is some data")
    val (wks2, product2) = ns.produce("hello", "This is some other data")
    for (k <- wks1.map(_.k)) k(product1)
    for (k <- wks2.map(_.k)) k(product2)

    dataAt(ns, Seq("hello")).value shouldBe Nil
    results.toList shouldBe Seq(Seq("This is some data"), Seq("This is some other data"))
  }

  "the hello world example" should "work" in {

    val ns: Namespace[String, Continuation[String]] = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[Seq[String]]    = mutable.ListBuffer.empty[Seq[String]]

    def testConsumer(k: Continuation[String])(channels: Seq[String]): Unit = {
      val (wk, product) = ns.consume(channels, Seq(Wildcard), k)
      wk.k(product)
    }

    def test(k: Continuation[String]): Unit = {
      ns.consume(Seq("helloworld"), Seq(Wildcard), k)
      val (wks1, product1) = ns.produce("helloworld", "world")
      val (wks2, product2) = ns.produce("world", "Hello World")
      for (k <- wks1.map(_.k)) k(product1)
      for (k <- wks2.map(_.k)) k(product2)
    }

    test(testConsumer(capture(results)))

    dataAt(ns, Seq("helloworld")).value shouldBe Nil
    results.toList shouldBe Seq(Seq("Hello World"))
  }
}
