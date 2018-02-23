package coop.rchain.mirror_world

import cats.implicits._

trait StorageActions {

  /* Consume */

  private[mirror_world] def extractDataCandidates[C, P, A, K](store: Store[C, P, A, K], cs: List[C], ps: List[P])(
      implicit m: Matcher[P, A]): Option[List[(A, C, Int)]] = {
    val options = cs.zip(ps).map {
      case (c, p) =>
        val as: Seq[(A, Int)] = store.getAs(c.pure[List]).zipWithIndex
        as.foldRight(None: Option[(A, C, Int)]) {
          case ((a, i), acc) =>
            m.isMatch(p, a) match {
              case None      => acc
              case Some(mat) => Some((mat, c, i))
            }
        }
    }
    options.sequence[Option, (A, C, Int)]
  }

  def consume[C, P, A, K](store: Store[C, P, A, K], cs: List[C], ps: List[P], k: K)(implicit m: Matcher[P, A]): Option[(K, List[A])] = {
    extractDataCandidates(store, cs, ps) match {
      case None =>
        store.putK(cs, ps, k)
        for (c <- cs) store.joinMap.addBinding(c, cs)
        None
      case Some(acis) =>
        acis.foreach {
          case (_, c, i) =>
            store.removeA(c.pure[List], i)
            store.removeK(cs, i)
            ignore { store.joinMap.removeBinding(c, cs) }
        }
        Some((k, acis.map(_._1)))
    }
  }

  /* Produce */

  private[mirror_world] def extractProduceCandidates[C, P, A, K](store: Store[C, P, A, K], groupedKeys: List[List[C]], c: C, data: A)(
      implicit m: Matcher[P, A]): Option[(K, List[(A, C, Int)])] = {
    groupedKeys.foldRight(None: Option[(K, List[(A, C, Int)])]) { (cs: List[C], acc) =>
      store.getK(cs).flatMap {
        case (ps, k) =>
          extractDataCandidates(store, c.pure[List], ps) match {
            case None       => acc
            case Some(acis) => Some((k, acis))
          }
      }
    }
  }

  def produce[C, P, A, K](store: Store[C, P, A, K], channel: C, data: A)(implicit m: Matcher[P, A]): Option[(K, List[A])] = {
    val ss: List[List[C]] = store.joinMap.get(channel).toList.flatten
    store.putA(channel.pure[List], data)
    extractProduceCandidates(store, ss, channel, data).map {
      case (k, acis) =>
        acis.foreach {
          case (_, c, i) =>
            store.removeA(c.pure[List], i)
            store.removeK(c.pure[List], i)
            ignore { store.joinMap.remove(c) }
        }
        (k, acis.map(_._1))
    }
  }
}
