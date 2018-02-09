package coop.rchain.shitheap

import org.scalatest._

import scala.collection.mutable
import coop.rchain.shitheap.{ignore => myIgnore}

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
class NamespaceTest extends FlatSpec {

  val ts: Tuplespace[String] = mutable.Map.empty
  val ns: Namespace[String]  = new Namespace(ts)

  def forMsgFromWorld(env: Env[String], msg: List[String]): Unit =
    println(msg)

  def forWorldFromHelloworld(env: Env[String], world: List[String]): Unit =
    ns.consume(channels = world, patterns = List(Wildcard), code = forMsgFromWorld, env = env.clone(), persistent = false)

  def worldScope(env: Env[String]): Unit = {
    // myIgnore { env.put("world", "world") }
    ns.produce(channel = "helloworld", product = "world")
    ns.produce(channel = "world", product = "Hello World")
  }

  def helloWorldScope(env: Env[String]): Unit = {
    myIgnore { env.put("helloworld", "helloworld") }
    ns.consume(channels = List(env("helloworld")),
               patterns = List(Wildcard),
               code = forWorldFromHelloworld,
               env = env.clone(),
               persistent = true)
    worldScope(env.clone())
  }

  "things" should "work" in {
    println("this is it")
    helloWorldScope(mutable.Map.empty)
  }
}
