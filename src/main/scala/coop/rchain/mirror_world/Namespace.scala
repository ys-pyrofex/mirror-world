package coop.rchain.mirror_world

import cats.implicits._

class Namespace[A](val tupleSpace: Tuplespace[A]) {

  /* Consume */

  def extractDataCandidates(channels: List[Channel], patterns: List[Pattern]): List[ConsumeCandidate[A]] =
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
    }

  def storeWaitingContinuation(channels: List[Channel], patterns: List[Pattern], k: Continuation[A]): Unit = {
    val waitingContinuation: WaitingContinuation[A] = WaitingContinuation[A](patterns, k)
    tupleSpace.get(channels) match {
      case Some(subspace) =>
        ignore { subspace.appendWaitingContinuation(waitingContinuation) }
      case None =>
        ignore { tupleSpace.put(channels, Subspace.empty[A].appendWaitingContinuation(waitingContinuation)) }
    }
  }

  def consume(channels: List[Channel], patterns: List[Pattern], k: Continuation[A]): Unit = {
    val chosenCandidates = extractDataCandidates(channels, patterns)
    if (chosenCandidates.nonEmpty) {
      k(chosenCandidates.map(_._1))
    } else {
      storeWaitingContinuation(channels, patterns, k)
    }
  }

  /* Produce */

  def matchesAt(patterns: List[Pattern], candidateChannelPosition: Int, channel: String): Boolean =
    patterns.lift(candidateChannelPosition).exists(_.isMatch(channel))

  def extractProduceCandidates(keyCandidates: List[List[Channel]], channel: String): List[(List[Channel], Int)] =
    for {
      candidateChannel         <- keyCandidates
      candidateChannelPosition <- candidateChannel.indexOf(channel).pure[List]
      subspace                 <- tupleSpace.get(candidateChannel).toList
      (k, ki)                  <- subspace.waitingContinuations.zipWithIndex if matchesAt(k.patterns, candidateChannelPosition, channel)
    } yield {
      (candidateChannel, ki)
    }

  def consumeContinuation(chosenCandidate: (List[Channel], Int)): Option[WaitingContinuation[A]] =
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
        ignore { tupleSpace.put(List(channel), Subspace.empty[A].appendData(product)) }
    }

  def produce(channel: Channel, product: A): Unit = {
    val candidates = tupleSpace.keys.toList.filter(_.exists(_.contains(channel)))
    val consumers  = extractProduceCandidates(candidates, channel)
    val dewers     = consumers.flatMap(consumer => consumeContinuation(consumer).toList)
    if (dewers.nonEmpty) {
      for (consumedK <- dewers) consumedK.k(singleton(product))
    } else {
      storeProduct(channel, product)
    }
  }
}
