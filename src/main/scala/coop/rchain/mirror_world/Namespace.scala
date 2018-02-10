package coop.rchain.mirror_world

import cats.implicits._

class Namespace[A, K](val tupleSpace: Tuplespace[A, K]) {

  /* Consume */

  def extractDataCandidates(channels: Seq[Channel], patterns: Seq[Pattern]): Seq[A] =
    channels.zipWithIndex.flatMap {
      case (channel, channelIndex) =>
        tupleSpace
          .get(channel.pure[List])
          .map { (subspace: Subspace[A, K]) =>
            subspace.data.zipWithIndex
              .filter { case (datum, _) => patterns.lift(channelIndex).exists(_.isMatch(datum)) }
          }
          .toList
          .flatten
          .map(_._1)
    }

  def storeWaitingContinuation(channels: Seq[Channel], waitingContinuation: WaitingContinuation[K]): Unit =
    tupleSpace.get(channels) match {
      case Some(subspace) =>
        ignore { subspace.appendWaitingContinuation(waitingContinuation) }
      case None =>
        ignore { tupleSpace.put(channels, Subspace.empty[A, K].appendWaitingContinuation(waitingContinuation)) }
    }

  def consume(channels: Seq[Channel], patterns: Seq[Pattern], k: K): (WaitingContinuation[K], Seq[A]) = {
    val waitingContinuation: WaitingContinuation[K] = WaitingContinuation(patterns, k)
    val extractedProducts: Seq[A]                   = extractDataCandidates(channels, patterns)
    if (extractedProducts.isEmpty) {
      storeWaitingContinuation(channels, waitingContinuation)
      (waitingContinuation, Nil)
    } else {
      (waitingContinuation, extractedProducts)
    }
  }

  /* Produce */

  def matchesAt(patterns: Seq[Pattern], candidateChannelPosition: Int, channel: Channel): Boolean =
    patterns.lift(candidateChannelPosition).exists(_.isMatch(channel))

  def extractProduceCandidates(keyCandidates: Seq[Seq[Channel]], channel: Channel): Seq[(Seq[Channel], Int)] =
    for {
      candidateChannel         <- keyCandidates
      candidateChannelPosition <- candidateChannel.indexOf(channel).pure[List]
      subspace                 <- tupleSpace.get(candidateChannel).toList
      (k, ki)                  <- subspace.waitingContinuations.zipWithIndex if matchesAt(k.patterns, candidateChannelPosition, channel)
    } yield {
      (candidateChannel, ki)
    }

  def getContinuation(chosenCandidate: (Seq[Channel], Int)): Option[WaitingContinuation[K]] =
    chosenCandidate match {
      case (channels, waitingContinuationIndex) =>
        tupleSpace
          .get(channels)
          .flatMap(_.removeWaitingContinuationAtIndex(waitingContinuationIndex))
    }

  def storeData(channel: Channel, data: A): Unit =
    tupleSpace.get(channel.pure[List]) match {
      case Some(s) =>
        ignore { s.appendData(data) }
      case None =>
        ignore { tupleSpace.put(channel.pure[List], Subspace.empty[A, K].appendData(data)) }
    }

  def produce(channel: Channel, data: A): (Seq[WaitingContinuation[K]], Seq[A]) = {
    val keyCandidates: Seq[Seq[Channel]]                  = tupleSpace.keys.toList.filter(_.exists(_.contains(channel)))
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
