package coop.rchain.mirror_world

import javax.xml.bind.DatatypeConverter.printHexBinary

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
class Store[C, P, A, K] private (_keys: mutable.HashMap[String, List[C]],
                                 _ps: mutable.HashMap[String, List[P]],
                                 _as: mutable.HashMap[String, List[A]],
                                 _k: mutable.HashMap[String, K])(implicit sc: Serialize[C]) {

  def hashC(cs: List[C])(implicit sc: Serialize[C]): String =
    printHexBinary(hashBytes(cs.flatMap(sc.encode).toArray))

  private def putCs(channels: List[C]): Unit =
    _keys.update(hashC(channels), channels)

  def putA(channels: List[C], a: A): Unit = {
    val key = hashC(channels)
    putCs(channels)
    val as = _as.getOrElseUpdate(key, List.empty[A])
    _as.update(key, a +: as)
  }

  def putK(channels: List[C], patterns: List[P], k: K): Unit = {
    val key = hashC(channels)
    putCs(channels)
    val ps = _ps.getOrElseUpdate(key, List.empty[P])
    _ps.update(key, patterns ++ ps)
    _k.update(key, k)
  }

  def getAs(channels: List[C]): List[A] =
    _as.getOrElse(hashC(channels), Nil)

  def getK(curr: List[C]): Option[(List[P], K)] = {
    val key = hashC(curr)
    for {
      ps <- _ps.get(key)
      k  <- _k.get(key)
    } yield (ps, k)
  }

  def removeA(channels: List[C], index: Int): Unit = {
    val key = hashC(channels)
    for (as <- _as.get(key)) {
      _as.update(key, dropIndex(as, index))
    }
  }

  def removeK(channels: List[C], index: Int): Unit = {
    val key = hashC(channels)
    for (ps <- _ps.get(key)) {
      _ps.update(key, dropIndex(ps, index))
      _k.remove(key)
    }
  }
}

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
object Store {

  def empty[C, P, A, K](implicit sc: Serialize[C]): Store[C, P, A, K] = new Store[C, P, A, K](
    _keys = mutable.HashMap.empty[String, List[C]],
    _ps = mutable.HashMap.empty[String, List[P]],
    _as = mutable.HashMap.empty[String, List[A]],
    _k = mutable.HashMap.empty[String, K]
  )
}
