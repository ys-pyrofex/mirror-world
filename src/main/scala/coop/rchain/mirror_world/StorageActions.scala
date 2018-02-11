package coop.rchain.mirror_world

import cats.implicits._

trait StorageActions {

  private[mirror_world] def matchesAtIndex[T](patterns: Seq[Pattern], index: Int, matchCandidate: T): Boolean =
    patterns.lift(index).exists(_.isMatch(matchCandidate))

  /* Consume */

  private[mirror_world] def extractDataCandidates[A, K](ns: Storage[A, K], channels: Seq[Channel], patterns: Seq[Pattern]): Seq[A] =
    for {
      (channel, channelIndex) <- channels.zipWithIndex
      subspace                <- ns.tuplespace.get(channel.pure[List]).toList
      d                       <- subspace.data if matchesAtIndex(patterns, channelIndex, d)
    } yield d

  private[mirror_world] def storeWaitingContinuation[A, K](ns: Storage[A, K],
                                                           channels: Seq[Channel],
                                                           waitingContinuation: WaitingContinuation[K]): Unit =
    ns.tuplespace.get(channels) match {
      case Some(subspace) =>
        ignore { subspace.appendWaitingContinuation(waitingContinuation) }
      case None =>
        ignore { ns.tuplespace.put(channels, Subspace.empty[A, K].appendWaitingContinuation(waitingContinuation)) }
    }

  def consume[A, K](ns: Storage[A, K], channels: Seq[Channel], patterns: Seq[Pattern], k: K): (Seq[WaitingContinuation[K]], Seq[A]) = {
    val waitingContinuation: WaitingContinuation[K] = WaitingContinuation(patterns, k)
    val extractedProducts: Seq[A]                   = extractDataCandidates(ns, channels, patterns)
    if (extractedProducts.isEmpty) {
      storeWaitingContinuation(ns, channels, waitingContinuation)
    }
    (waitingContinuation.pure[List], extractedProducts)
  }

  /* Produce */

  private[mirror_world] def extractProduceCandidates[A, K](ns: Storage[A, K],
                                                           keyCandidates: Seq[Seq[Channel]],
                                                           channel: Channel): Seq[(Seq[Channel], Int)] =
    for {
      candidateChannels         <- keyCandidates
      candidateChannelsPosition <- candidateChannels.indexOf(channel).pure[List]
      subspace                  <- ns.tuplespace.get(candidateChannels).toList
      (k, ki)                   <- subspace.waitingContinuations.zipWithIndex if matchesAtIndex(k.patterns, candidateChannelsPosition, channel)
    } yield {
      (candidateChannels, ki)
    }

  private[mirror_world] def getContinuation[A, K](ns: Storage[A, K], chosenCandidate: (Seq[Channel], Int)): Option[WaitingContinuation[K]] =
    chosenCandidate match {
      case (channels, waitingContinuationIndex) =>
        ns.tuplespace
          .get(channels)
          .flatMap(_.removeWaitingContinuationAtIndex(waitingContinuationIndex))
    }

  private[mirror_world] def storeData[A, K](ns: Storage[A, K], channel: Channel, data: A): Unit =
    ns.tuplespace.get(channel.pure[List]) match {
      case Some(s) =>
        ignore { s.appendData(data) }
      case None =>
        ignore { ns.tuplespace.put(channel.pure[List], Subspace.empty[A, K].appendData(data)) }
    }

  def produce[A, K](ns: Storage[A, K], channel: Channel, data: A): (Seq[WaitingContinuation[K]], Seq[A]) = {
    val keyCandidates: Seq[Seq[Channel]]                  = ns.tuplespace.keys.toSeq.filter(_.exists(_.contains(channel)))
    val produceCandidates: Seq[(Seq[Channel], Int)]       = extractProduceCandidates(ns, keyCandidates, channel)
    val waitingContinuations: Seq[WaitingContinuation[K]] = produceCandidates.flatMap(chosen => getContinuation(ns, chosen).toList)
    if (waitingContinuations.isEmpty) {
      storeData(ns, channel, data)
    }
    (waitingContinuations, data.pure[List])
  }
}
