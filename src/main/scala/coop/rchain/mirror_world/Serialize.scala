package coop.rchain.mirror_world

import java.nio.charset.StandardCharsets

trait Serialize[T] {

  def encode(a: T): Array[Byte]

  def decode(bytes: Array[Byte]): T
}

object Serialize {

  implicit object stringSerializer extends Serialize[String] {

    def encode(a: String): Array[Byte] =
      a.getBytes(StandardCharsets.UTF_8)

    def decode(bytes: Array[Byte]): String =
      new String(bytes, StandardCharsets.UTF_8)
  }
}
