package coop.rchain.mirror_world

import cats.implicits._

trait StorageActions {

  private[mirror_world] def matchExists[P, A](patterns: Seq[P], matchCandidate: A)(implicit m: Matcher[P, A]): Boolean =
    patterns.exists((P: P) => m.isMatch(P, matchCandidate))

  /* Consume */

  private[mirror_world] def extractDataCandidates[C, P, A, K](store: Storage[C, P, A, K], cs: Seq[C], ps: Seq[P])(
      implicit m: Matcher[P, A]): Seq[A] =
    for {
      c <- cs
      a <- store.tuplespace.as(c.pure[List]) if matchExists(ps, a)
    } yield a

  def consume[C, P, A, K](store: Storage[C, P, A, K], cs: Seq[C], ps: Seq[P], k: K)(
      implicit m: Matcher[P, A]): (Seq[(K, Seq[P])], Seq[A]) = {
    val extractedProducts: Seq[A] = extractDataCandidates(store, cs, ps)
    if (extractedProducts.isEmpty) {
      store.tuplespace.putK(cs, ps, k)
    }
    ((k, ps).pure[List], extractedProducts)
  }

  /* Produce */

  private[mirror_world] def extractProduceCandidates[C, P, A, K](store: Storage[C, P, A, K], keys: Seq[Seq[C]], C: C, data: A)(
      implicit m: Matcher[P, A]): Seq[(Seq[C], Int)] =
    for {
      key     <- keys
      (ps, i) <- store.tuplespace.ps(key).zipWithIndex if matchExists(ps, data)
    } yield {
      (key, i)
    }

  private[mirror_world] def getWaiters[C, P, A, K](store: Storage[C, P, A, K], candidate: (Seq[C], Int)): Option[(K, Seq[P])] =
    candidate match {
      case (cs, waitingContinuationIndex) =>
        store.tuplespace.removeK(cs, waitingContinuationIndex)
    }

  def produce[C, P, A, K](store: Storage[C, P, A, K], c: C, a: A)(implicit m: Matcher[P, A]): (Seq[(K, Seq[P])], Seq[A]) = {
    val produceCandidates = extractProduceCandidates(store, store.tuplespace.keys.toList, c, a).reverse
    val waiters           = produceCandidates.flatMap(chosen => getWaiters(store, chosen).toList)
    if (waiters.isEmpty) {
      store.tuplespace.putA(c.pure[List], a)
    }
    (waiters, a.pure[List])
  }
}
