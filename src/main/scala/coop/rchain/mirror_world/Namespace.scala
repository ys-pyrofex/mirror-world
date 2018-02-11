package coop.rchain.mirror_world

import cats.implicits._

class Namespace[A, K](val tuplespace: Tuplespace[A, K]) {

  /* Consume */

  def extractDataCandidates(channels: Seq[Channel], patterns: Seq[Pattern]): Seq[A] =
    for {
      (channel, channelIndex) <- channels.zipWithIndex
      subspace                <- tuplespace.get(channel.pure[List]).toList
      d                       <- subspace.data if Namespace.matchesAtIndex(patterns, channelIndex, d)
    } yield d

  def storeWaitingContinuation(channels: Seq[Channel], waitingContinuation: WaitingContinuation[K]): Unit =
    tuplespace.get(channels) match {
      case Some(subspace) =>
        ignore { subspace.appendWaitingContinuation(waitingContinuation) }
      case None =>
        ignore { tuplespace.put(channels, Subspace.empty[A, K].appendWaitingContinuation(waitingContinuation)) }
    }

  def consume(channels: Seq[Channel], patterns: Seq[Pattern], k: K): (Seq[WaitingContinuation[K]], Seq[A]) = {
    val waitingContinuation: WaitingContinuation[K] = WaitingContinuation(patterns, k)
    val extractedProducts: Seq[A]                   = extractDataCandidates(channels, patterns)
    if (extractedProducts.isEmpty) {
      storeWaitingContinuation(channels, waitingContinuation)
    }
    (waitingContinuation.pure[List], extractedProducts)
  }

  /* Produce */

  def extractProduceCandidates(keyCandidates: Seq[Seq[Channel]], channel: Channel): Seq[(Seq[Channel], Int)] =
    for {
      candidateChannels         <- keyCandidates
      candidateChannelsPosition <- candidateChannels.indexOf(channel).pure[List]
      subspace                  <- tuplespace.get(candidateChannels).toList
      (k, ki)                   <- subspace.waitingContinuations.zipWithIndex if Namespace.matchesAtIndex(k.patterns, candidateChannelsPosition, channel)
    } yield {
      (candidateChannels, ki)
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
    val keyCandidates: Seq[Seq[Channel]]                  = tuplespace.keys.toSeq.filter(_.exists(_.contains(channel)))
    val produceCandidates: Seq[(Seq[Channel], Int)]       = extractProduceCandidates(keyCandidates, channel)
    val waitingContinuations: Seq[WaitingContinuation[K]] = produceCandidates.flatMap(chosen => getContinuation(chosen).toList)
    if (waitingContinuations.isEmpty) {
      storeData(channel, data)
    }
    (waitingContinuations, data.pure[List])
  }
}

object Namespace {

  private[mirror_world] def matchesAtIndex[T](patterns: Seq[Pattern], index: Int, matchCandidate: T): Boolean =
    patterns.lift(index).exists(_.isMatch(matchCandidate))
}
