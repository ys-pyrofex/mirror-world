package coop.rchain.mirror_world

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
class Store[A, K] private (_keys: mutable.HashMap[String, Seq[Channel]],
                           _ps: mutable.HashMap[String, Seq[Seq[Pattern]]],
                           _as: mutable.HashMap[String, Seq[A]],
                           _ks: mutable.HashMap[String, Seq[K]]) {

  def keys: Seq[Seq[Channel]]                       = _keys.values.toSeq
  def ps(channels: Seq[Channel]): Seq[Seq[Pattern]] = _ps.getOrElse(hashChannels(channels), Nil)
  def as(channels: Seq[Channel]): Seq[A]            = _as.getOrElse(hashChannels(channels), Nil)
  def ks(channels: Seq[Channel]): Seq[K]            = _ks.getOrElse(hashChannels(channels), Nil)

  private def addKey(channels: Seq[Channel]): Unit =
    _keys.update(hashChannels(channels), channels)

  def removeA(channels: Seq[Channel], index: Int): Option[A] = {
    val key = hashChannels(channels)
    for {
      _  <- _keys.get(key)
      as <- _as.get(key)
    } yield {
      val (a, nas) = extractIndex(as, index)
      _as.update(key, nas)
      a
    }
  }

  def removeK(channels: Seq[Channel], index: Int): Option[(K, Seq[Pattern])] = {
    val key = hashChannels(channels)
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

  def putA(channels: Seq[Channel], a: A): Unit = {
    val key = hashChannels(channels)
    addKey(channels)
    val as = _as.getOrElseUpdate(key, Seq.empty[A])
    _as.update(key, a +: as)
  }

  def putK(channels: Seq[Channel], patterns: Seq[Pattern], k: K): Unit = {
    val key = hashChannels(channels)
    addKey(channels)
    val ps = _ps.getOrElseUpdate(key, Seq.empty[Seq[Pattern]])
    val ks = _ks.getOrElseUpdate(key, Seq.empty[K])
    _ps.update(key, patterns +: ps)
    _ks.update(key, k +: ks)
  }
}

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
object Store {

  def empty[A, K]: Store[A, K] = new Store[A, K](
    _keys = mutable.HashMap.empty[String, Seq[Channel]],
    _ps = mutable.HashMap.empty[String, Seq[Seq[Pattern]]],
    _as = mutable.HashMap.empty[String, Seq[A]],
    _ks = mutable.HashMap.empty[String, Seq[K]]
  )
}
