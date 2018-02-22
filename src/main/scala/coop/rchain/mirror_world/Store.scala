package coop.rchain.mirror_world

import javax.xml.bind.DatatypeConverter.printHexBinary

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
class Store[C, P, A, K] private (_keys: mutable.HashMap[String, Seq[C]],
                                 _ps: mutable.HashMap[String, Seq[Seq[P]]],
                                 _as: mutable.HashMap[String, Seq[A]],
                                 _ks: mutable.HashMap[String, Seq[K]])(implicit sc: Serialize[C]) {

  def hashC(cs: Seq[C])(implicit sc: Serialize[C]): String =
    printHexBinary(hashBytes(cs.flatMap(sc.encode).toArray))

  def keys: Seq[Seq[C]]                 = _keys.values.toSeq
  def ps(channels: Seq[C]): Seq[Seq[P]] = _ps.getOrElse(hashC(channels), Nil)
  def as(channels: Seq[C]): Seq[A]      = _as.getOrElse(hashC(channels), Nil)
  def ks(channels: Seq[C]): Seq[K]      = _ks.getOrElse(hashC(channels), Nil)

  private def addKey(channels: Seq[C]): Unit =
    _keys.update(hashC(channels), channels)

  def removeA(channels: Seq[C], index: Int): Option[A] = {
    val key = hashC(channels)
    for {
      _  <- _keys.get(key)
      as <- _as.get(key)
    } yield {
      val (a, nas) = extractIndex(as, index)
      _as.update(key, nas)
      a
    }
  }

  def removeK(channels: Seq[C], index: Int): Option[(K, Seq[P])] = {
    val key = hashC(channels)
    for {
      _  <- _keys.get(key)
      ps <- _ps.get(key)
      ks <- _ks.get(key)
    } yield {
      val (p, nps) = extractIndex(ps, index)
      val (k, nks) = extractIndex(ks, index)
      _ps.update(key, nps)
      _ks.update(key, nks)
      (k, p)
    }
  }

  def putA(channels: Seq[C], a: A): Unit = {
    val key = hashC(channels)
    addKey(channels)
    val as = _as.getOrElseUpdate(key, Seq.empty[A])
    _as.update(key, a +: as)
  }

  def putK(channels: Seq[C], patterns: Seq[P], k: K): Unit = {
    val key = hashC(channels)
    addKey(channels)
    val ps = _ps.getOrElseUpdate(key, Seq.empty[Seq[P]])
    val ks = _ks.getOrElseUpdate(key, Seq.empty[K])
    _ps.update(key, patterns +: ps)
    _ks.update(key, k +: ks)
  }
}

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
object Store {

  def empty[C, P, A, K](implicit sc: Serialize[C]): Store[C, P, A, K] = new Store[C, P, A, K](
    _keys = mutable.HashMap.empty[String, Seq[C]],
    _ps = mutable.HashMap.empty[String, Seq[Seq[P]]],
    _as = mutable.HashMap.empty[String, Seq[A]],
    _ks = mutable.HashMap.empty[String, Seq[K]]
  )
}
