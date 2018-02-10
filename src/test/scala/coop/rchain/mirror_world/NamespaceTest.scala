package coop.rchain.mirror_world

import coop.rchain.mirror_world.{ignore => myIgnore}
import org.scalatest._

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
class NamespaceTest extends FlatSpec {

  "things" should "work" in {

    val ts: Tuplespace[String] = mutable.Map.empty
    val ns: Namespace[String]  = new Namespace(ts)

    def testConsumer(code: Code[String])(env: Env[String], world: List[String]): Unit =
      ns.consume(channels = world, patterns = List(Wildcard), code = code, env = env.clone(), persistent = false)

    def test(env: Env[String], code: Code[String]): Unit = {
      myIgnore { env.put("helloworld", "helloworld") }
      ns.consume(channels = List(env("helloworld")), patterns = List(Wildcard), code = code, env = env.clone(), persistent = true)
      ns.produce(channel = "helloworld", product = "world")
      ns.produce(channel = "world", product = "Hello World")
    }

    val results = mutable.ListBuffer.empty[List[String]]

    test(mutable.Map.empty, testConsumer((_, msg) => myIgnore { results += msg }))

    assert(List(List("Hello World")) === results.toList)
  }
}
