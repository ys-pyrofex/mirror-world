package coop.rchain.mirror_world

import cats.implicits._

class Namespace[A](val tupleSpace: Tuplespace[A]) {

  /* Consume */

  def extractDataCandidates(channels: Seq[Channel], patterns: Seq[Pattern]): Seq[A] =
    channels.zipWithIndex.flatMap {
      case (channel, channelIndex) =>
        tupleSpace
          .get(singleton(channel))
          .map { (subspace: Subspace[A]) =>
            subspace.data.zipWithIndex
              .filter { case (datum, _) => patterns.lift(channelIndex).exists(_.isMatch(datum)) }
          }
          .toList
          .flatten
          .map(_._1)
    }

  def storeWaitingContinuation(channels: Seq[Channel], patterns: Seq[Pattern], k: Continuation[A]): Unit = {
    val waitingContinuation: WaitingContinuation[A] = WaitingContinuation(patterns, k)
    tupleSpace.get(channels) match {
      case Some(subspace) =>
        ignore { subspace.appendWaitingContinuation(waitingContinuation) }
      case None =>
        ignore { tupleSpace.put(channels, Subspace.empty[A].appendWaitingContinuation(waitingContinuation)) }
    }
  }

  def consume(channels: Seq[Channel], patterns: Seq[Pattern], k: Continuation[A]): Unit = {
    val chosenCandidates: Seq[A] = extractDataCandidates(channels, patterns)
    if (chosenCandidates.isEmpty) {
      storeWaitingContinuation(channels, patterns, k)
    } else {
      k(chosenCandidates)
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

  def getContinuation(chosenCandidate: (Seq[Channel], Int)): Option[WaitingContinuation[A]] =
    chosenCandidate match {
      case (channels, waitingContinuationIndex) =>
        tupleSpace
          .get(channels)
          .flatMap(_.removeWaitingContinuationAtIndex(waitingContinuationIndex))
    }

  def storeProduct(channel: Channel, product: A): Unit =
    tupleSpace.get(singleton(channel)) match {
      case Some(s) =>
        ignore { s.appendData(product) }
      case None =>
        ignore { tupleSpace.put(singleton(channel), Subspace.empty[A].appendData(product)) }
    }

  def produce(channel: Channel, product: A): Unit = {
    val keyCandidates: Seq[Seq[Channel]]                  = tupleSpace.keys.toList.filter(_.exists(_.contains(channel)))
    val produceCandidates: Seq[(Seq[Channel], Int)]       = extractProduceCandidates(keyCandidates, channel)
    val waitingContinuations: Seq[WaitingContinuation[A]] = produceCandidates.flatMap(chosen => getContinuation(chosen).toList)
    if (waitingContinuations.isEmpty) {
      storeProduct(channel, product)
    } else {
      for (waitingK <- waitingContinuations) waitingK.k(singleton(product))
    }
  }
}
