package coop.rchain.mirror_world

import cats.implicits._

class Namespace[A, K](val tuplespace: Tuplespace[A, K]) {

  def matchesAtIndex[T](patterns: Seq[Pattern], index: Int, matchCandidate: T): Boolean =
    patterns.lift(index).exists(_.isMatch(matchCandidate))

  /* Consume */

  def extractDataCandidates(channels: Seq[Channel], patterns: Seq[Pattern]): Seq[A] =
    channels.zipWithIndex.flatMap {
      case (channel, channelIndex) =>
        tuplespace
          .get(channel.pure[List])
          .map { (subspace: Subspace[A, K]) =>
            subspace.data.zipWithIndex
              .filter { case (datum, _) => matchesAtIndex(patterns, channelIndex, datum) }
          }
          .toList
          .flatten
          .map(_._1)
    }

  def storeWaitingContinuation(channels: Seq[Channel], waitingContinuation: WaitingContinuation[K]): Unit =
    tuplespace.get(channels) match {
      case Some(subspace) =>
        ignore { subspace.appendWaitingContinuation(waitingContinuation) }
      case None =>
        ignore { tuplespace.put(channels, Subspace.empty[A, K].appendWaitingContinuation(waitingContinuation)) }
    }

  def consume(channels: Seq[Channel], patterns: Seq[Pattern], k: K): (WaitingContinuation[K], Seq[A]) = {
    val waitingContinuation: WaitingContinuation[K] = WaitingContinuation(patterns, k)
    val extractedProducts: Seq[A]                   = extractDataCandidates(channels, patterns)
    if (extractedProducts.isEmpty) {
      storeWaitingContinuation(channels, waitingContinuation)
    }
    (waitingContinuation, extractedProducts)
  }

  /* Produce */

  def extractProduceCandidates(keyCandidates: Seq[Seq[Channel]], channel: Channel): Seq[(Seq[Channel], Int)] =
    for {
      candidateChannel         <- keyCandidates
      candidateChannelPosition <- candidateChannel.indexOf(channel).pure[List]
      subspace                 <- tuplespace.get(candidateChannel).toList
      (k, ki)                  <- subspace.waitingContinuations.zipWithIndex if matchesAtIndex(k.patterns, candidateChannelPosition, channel)
    } yield {
      (candidateChannel, ki)
    }

  def getContinuation(chosenCandidate: (Seq[Channel], Int)): Option[WaitingContinuation[K]] =
    chosenCandidate match {
      case (channels, waitingContinuationIndex) =>
        tuplespace
          .get(channels)
          .flatMap(_.removeWaitingContinuationAtIndex(waitingContinuationIndex))
    }

  def storeData(channel: Channel, data: A): Unit =
    tuplespace.get(channel.pure[List]) match {
      case Some(s) =>
        ignore { s.appendData(data) }
      case None =>
        ignore { tuplespace.put(channel.pure[List], Subspace.empty[A, K].appendData(data)) }
    }

  def produce(channel: Channel, data: A): (Seq[WaitingContinuation[K]], Seq[A]) = {
    val keyCandidates: Seq[Seq[Channel]]                  = tuplespace.keys.toList.filter(_.exists(_.contains(channel)))
    val produceCandidates: Seq[(Seq[Channel], Int)]       = extractProduceCandidates(keyCandidates, channel)
    val waitingContinuations: Seq[WaitingContinuation[K]] = produceCandidates.flatMap(chosen => getContinuation(chosen).toList)
    if (waitingContinuations.isEmpty) {
      storeData(channel, data)
      (Seq.empty[WaitingContinuation[K]], data.pure[List])
    } else {
      (waitingContinuations, data.pure[List])
    }
  }
}
