package coop.rchain.mirror_world

import coop.rchain.mirror_world.{ignore => ign}
import org.scalatest._

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
class NamespaceTest extends FlatSpec {

  "produce" should "persist a piece of data in the tuplespace" in {

    val ns: Namespace[String] = new Namespace(mutable.Map.empty)

    ns.produce("helloworld", "world")

    assert(ns.tupleSpace.get(singleton("helloworld")).map(_.data).contains(List("world")))
  }

  "produce followed consume" should "cause the persisted data to be handed to the given continuation" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.produce("helloworld", "world")
    ns.consume(List("helloworld"), List(Wildcard), as => ign(results += as))

    assert(List(List("world")) === results.toList)
  }

  "consume on multiple channels followed by produce" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.consume(List("helloworld", "quux"), List(Wildcard, Wildcard), as => ign(results += as))
    ns.produce("quux", "Hello World")

    assert(List(List("Hello World")) === results.toList)
  }

  "consume on multiple channels followed by consume on a same channel followed by produce" should "work" in {

    val ns: Namespace[String]                      = new Namespace(mutable.Map.empty)
    val results1: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]
    val results2: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.consume(List("helloworld", "quux"), List(StringMatch("helloworld"), StringMatch("quux")), as => ign(results1 += as))
    ns.consume(List("quux"), List(Wildcard), as => ign(results2 += as))
    ns.produce("quux", "Hello World")

    assert(List(List("Hello World")) === results1.toList && List(List("Hello World")) === results2.toList)
  }

  "two consumes on a single channel followed by produce" should "work" in {

    val ns: Namespace[String]                     = new Namespace(mutable.Map.empty)
    val results: mutable.ListBuffer[List[String]] = mutable.ListBuffer.empty[List[String]]

    ns.consume(List("helloworld"), List(Wildcard), as => ign(results += as))
    ns.consume(List("helloworld"), List(StringMatch("helloworld")), as => ign(results += as))
    ns.produce(channel = "helloworld", product = "Hello World")
    ns.produce(channel = "helloworld", product = "Hello World")

    assert(List(List("Hello World"), List("Hello World")) === results.toList)
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

    test(testConsumer(as => ign(results += as)))

    assert(List(List("Hello World")) === results.toList)
  }
}
