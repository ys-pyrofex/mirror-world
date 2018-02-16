package coop.rchain.mirror_world

import cats.implicits._

trait StorageActions {

  private[mirror_world] def matchesAtIndex[T](patterns: Seq[Pattern], index: Int, matchCandidate: T)(
      implicit m: Matcher[Pattern, T]): Boolean =
    patterns.lift(index).exists((pattern: Pattern) => m.isMatch(pattern, matchCandidate))

  /* Consume */

  private[mirror_world] def extractDataCandidates[A, K](ns: Storage[A, K], channels: Seq[Channel], patterns: Seq[Pattern]): Seq[A] =
    for {
      (channel, channelIndex) <- channels.zipWithIndex
      d                       <- ns.tuplespace.as(channel.pure[List]) if matchesAtIndex(patterns, channelIndex, d)
    } yield d

  def consume[A, K](ns: Storage[A, K], channels: Seq[Channel], patterns: Seq[Pattern], k: K): (Seq[(K, Seq[Pattern])], Seq[A]) = {
    val extractedProducts: Seq[A] = extractDataCandidates(ns, channels, patterns)
    if (extractedProducts.isEmpty) {
      ns.tuplespace.putK(channels, patterns, k)
    }
    ((k, patterns).pure[List], extractedProducts)
  }

  /* Produce */

  private[mirror_world] def extractProduceCandidates[A, K](ns: Storage[A, K],
                                                           keyCandidates: Seq[Seq[Channel]],
                                                           channel: Channel): Seq[(Seq[Channel], Int)] =
    for {
      channels  <- keyCandidates
      (ptns, i) <- ns.tuplespace.ps(channels).zipWithIndex if matchesAtIndex(ptns, channels.indexOf(channel), channel)
    } yield {
      (channels, i)
    }

  private[mirror_world] def getContinuation[A, K](ns: Storage[A, K], chosenCandidate: (Seq[Channel], Int)): Option[(K, Seq[Pattern])] =
    chosenCandidate match {
      case (channels, waitingContinuationIndex) =>
        ns.tuplespace.removeK(channels, waitingContinuationIndex)
    }

  def produce[A, K](ns: Storage[A, K], channel: Channel, data: A): (Seq[(K, Seq[Pattern])], Seq[A]) = {
    val keyCandidates: Seq[Seq[Channel]]             = ns.tuplespace.keys.filter(_.exists(_.contains(channel))).toList
    val produceCandidates: Seq[(Seq[Channel], Int)]  = extractProduceCandidates(ns, keyCandidates, channel).reverse
    val waitingContinuations: Seq[(K, Seq[Pattern])] = produceCandidates.flatMap(chosen => getContinuation(ns, chosen).toList)
    if (waitingContinuations.isEmpty) {
      ns.tuplespace.putA(channel.pure[List], data)
    }
    (waitingContinuations, data.pure[List])
  }
}
