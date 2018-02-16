package coop.rchain.mirror_world

import cats.implicits._

trait StorageActions {

  private[mirror_world] def matchExists[T](patterns: Seq[Pattern], matchCandidate: T)(implicit m: Matcher[Pattern, T]): Boolean =
    patterns.exists((pattern: Pattern) => m.isMatch(pattern, matchCandidate))

  /* Consume */

  private[mirror_world] def extractDataCandidates[A, K](ns: Storage[A, K], channels: Seq[Channel], patterns: Seq[Pattern]): Seq[A] =
    for {
      channel <- channels
      a       <- ns.tuplespace.as(channel.pure[List]) if matchExists(patterns, channel)
    } yield a

  def consume[A, K](ns: Storage[A, K], channels: Seq[Channel], patterns: Seq[Pattern], k: K): (Seq[(K, Seq[Pattern])], Seq[A]) = {
    val extractedProducts: Seq[A] = extractDataCandidates(ns, channels, patterns)
    if (extractedProducts.isEmpty) {
      ns.tuplespace.putK(channels, patterns, k)
    }
    ((k, patterns).pure[List], extractedProducts)
  }

  /* Produce */

  private[mirror_world] def extractProduceCandidates[A, K](ns: Storage[A, K],
                                                           keys: Seq[Seq[Channel]],
                                                           channel: Channel): Seq[(Seq[Channel], Int)] =
    for {
      key           <- keys
      (patterns, i) <- ns.tuplespace.ps(key).zipWithIndex if matchExists(patterns, channel)
    } yield {
      (key, i)
    }

  private[mirror_world] def getContinuation[A, K](ns: Storage[A, K], chosenCandidate: (Seq[Channel], Int)): Option[(K, Seq[Pattern])] =
    chosenCandidate match {
      case (channels, waitingContinuationIndex) =>
        ns.tuplespace.removeK(channels, waitingContinuationIndex)
    }

  def produce[A, K](ns: Storage[A, K], channel: Channel, data: A): (Seq[(K, Seq[Pattern])], Seq[A]) = {
    val keyCandidates: Seq[Seq[Channel]]             = ns.tuplespace.keys.toList
    val produceCandidates: Seq[(Seq[Channel], Int)]  = extractProduceCandidates(ns, keyCandidates, channel).reverse
    val waitingContinuations: Seq[(K, Seq[Pattern])] = produceCandidates.flatMap(chosen => getContinuation(ns, chosen).toList)
    if (waitingContinuations.isEmpty) {
      ns.tuplespace.putA(channel.pure[List], data)
    }
    (waitingContinuations, data.pure[List])
  }
}
