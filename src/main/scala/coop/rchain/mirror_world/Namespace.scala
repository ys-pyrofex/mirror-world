package coop.rchain.mirror_world

import cats.implicits._

class Namespace[A](val tupleSpace: Tuplespace[A]) {

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

  def consumeProducts(channels: List[Channel], chosenCandidates: List[ConsumeCandidate[A]]): List[A] =
    chosenCandidates.map(candidate => candidate._1)

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
      k(consumeProducts(channels, chosenCandidates))
    } else {
      storeWaitingContinuation(channels, patterns, k)
    }
  }

  def matchesAt(patterns: List[Pattern], candidateChannelPosition: Int, channel: String): Boolean =
    patterns.lift(candidateChannelPosition).exists(_.isMatch(channel))

  def extractProduceCandidates(keyCandidates: List[List[Channel]],
                               channel: String): List[(List[ProduceCandidate], (List[Channel], Int))] = {
    for {
      candidateChannel         <- keyCandidates
      candidateChannelPosition <- candidateChannel.indexOf(channel).pure[List]
      subspace                 <- tupleSpace.get(candidateChannel).toList
      (k, ki)                  <- subspace.waitingContinuations.zipWithIndex if matchesAt(k.patterns, candidateChannelPosition, channel)
      produceCandidates = candidateChannel.lift(candidateChannelPosition).toList if produceCandidates.nonEmpty
    } yield {
      (produceCandidates, (candidateChannel, ki))
    }
  }

  def consumeContinuation(chosenCandidate: (List[ProduceCandidate], (List[Channel], Int)),
                          product: A): Option[(WaitingContinuation[A], List[A])] =
    chosenCandidate match {
      case (produceCandidates, (candidateChannelKey, waitingContinuationIndex)) =>
        val products: List[A] = produceCandidates.map(_ => product)
        tupleSpace
          .get(candidateChannelKey)
          .flatMap(_.removeWaitingContinuationAtIndex(waitingContinuationIndex))
          .map((value: WaitingContinuation[A]) => (value, products))
    }

  /** Store product at channel
    */
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
    val dewers     = consumers.flatMap(consumer => consumeContinuation(consumer, product).toList)
    if (dewers.nonEmpty) {
      for ((consumedK, products) <- dewers) consumedK.k(products)
    } else {
      storeProduct(channel, product)
    }
  }
}
