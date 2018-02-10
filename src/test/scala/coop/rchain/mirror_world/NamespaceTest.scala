package coop.rchain.mirror_world

import coop.rchain.mirror_world.{ignore => myIgnore}
import org.scalatest._

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
class NamespaceTest extends FlatSpec {

  "produce" should "persist a piece of data in the tuplespace" in {

    val ns: Namespace[String] = new Namespace(mutable.Map.empty)

    ns.produce(channel = "helloworld", product = "world")

    assert(ns.tupleSpace.get(singleton("helloworld")).map(_.data).contains(List("world")))
  }

  "produce followed consume" should "cause the persisted data to be handed to the given continuation" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.produce(channel = "helloworld", product = "world")
    ns.consume(channels = List("helloworld"), patterns = List(Wildcard), k = (msg) => myIgnore(results += msg))

    assert(List(List("world")) === results.toList)
  }

  "consume on multiple channels followed by produce" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.consume(channels = List("helloworld", "quux"), patterns = List(Wildcard, Wildcard), k = msg => myIgnore(results += msg))
    ns.produce(channel = "quux", product = "Hello World")

    assert(List(List("Hello World")) === results.toList)
  }

  "two consumes on a single channel followed by produce" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.consume(channels = List("helloworld"), patterns = List(Wildcard), k = msg => myIgnore(results += msg))
    ns.consume(channels = List("helloworld"), patterns = List(Wildcard), k = msg => myIgnore(results += msg))
    ns.produce(channel = "helloworld", product = "Hello World")
    ns.produce(channel = "helloworld", product = "Hello World")

    assert(List(List("Hello World"), List("Hello World")) === results.toList)
  }

  "the hello world example" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    def testConsumer(k: Continuation[String])(channels: List[String]): Unit =
      ns.consume(channels = channels, patterns = List(Wildcard), k = k)

    def test(k: Continuation[String]): Unit = {
      ns.consume(channels = List("helloworld"), patterns = List(Wildcard), k = k)
      ns.produce(channel = "helloworld", product = "world")
      ns.produce(channel = "world", product = "Hello World")
    }

    test(testConsumer((msg) => myIgnore { results += msg }))

    assert(List(List("Hello World")) === results.toList)
  }
}
