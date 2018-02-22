package coop.rchain.mirror_world

trait Serialize[T] {

  def encode(a: T): Array[Byte]

  def decode(bytes: Array[Byte]): T
}
