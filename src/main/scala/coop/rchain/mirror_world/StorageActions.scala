package coop.rchain.mirror_world

import cats.implicits._

trait StorageActions {

  /* Consume */

  private[mirror_world] def extractDataCandidates[C, P, A, K](store: Storage[C, P, A, K], cs: List[C], ps: List[P])(
      implicit m: Matcher[P, A]): Option[List[(A, C, Int)]] = {
    val options = cs.zip(ps).map {
      case (c, p) =>
        val as: Seq[(A, Int)] = store.tuplespace.as(c.pure[List]).zipWithIndex
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

  def consume[C, P, A, K](store: Storage[C, P, A, K], cs: List[C], ps: List[P], k: K)(implicit m: Matcher[P, A]): Option[(K, List[A])] = {
    extractDataCandidates(store, cs, ps) match {
      case None =>
        store.tuplespace.putK(cs, ps, k)
        for (c <- cs) store.joinMap.addBinding(c, cs)
        // 0 -> [0 1 2]
        // 1 -> [0 1 2]
        // 2 -> [0 1 2]
        None
      case Some(acis) =>
        acis.foreach {
          case (a, c, i) =>
            store.tuplespace.removeA(c.pure[List], i)
            store.tuplespace.removeK(cs, i)
            ignore { store.joinMap.removeBinding(c, cs) }
          // 0 -> [0 1 2], [0 4 5]
          // 1 -> [0 1 2]
          // 2 -> [0 1 2]
          // 4 -> [0 4 5]
          // 5 -> [0 4 5]
        }
        Some((k, acis.map(_._1)))
    }
  }

  /* Produce */

  private[mirror_world] def extractProduceCandidates[C, P, A, K](store: Storage[C, P, A, K], groupedKeys: List[List[C]], c: C, data: A)(
      implicit m: Matcher[P, A]): Option[(K, List[(A, C, Int)])] = {
    groupedKeys.foldRight(None: Option[(K, List[(A, C, Int)])]) { (cs: List[C], acc) =>
      store.tuplespace.getK(cs).flatMap {
        case (ps, k) =>
          extractDataCandidates(store, c.pure[List], ps) match {
            case None       => acc
            case Some(acis) => Some((k, acis))
          }
      }
    }
  }

  def produce[C, P, A, K](store: Storage[C, P, A, K], channel: C, data: A)(implicit m: Matcher[P, A]): Option[(K, List[A])] = {
    val ss: List[List[C]] = store.joinMap.get(channel).toList.flatten
    store.tuplespace.putA(channel.pure[List], data)
    extractProduceCandidates(store, ss, channel, data).map {
      case (k, acis) =>
        acis.foreach {
          case (_, c, i) =>
            store.tuplespace.removeA(c.pure[List], i)
            ignore { store.joinMap.remove(c) }
            // 0 -> [0 1 2], [0 4 5]
            // 1 -> [0 1 2]
            // 2 -> [0 1 2]
            // 4 -> [0 4 5]
            // 5 -> [0 4 5]
            store.tuplespace.removeK(c.pure[List], i)
        }
        (k, acis.map(_._1))
    }
  }

}
