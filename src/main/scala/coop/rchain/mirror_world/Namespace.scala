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
    for ((candidate, candidateIndex) <- chosenCandidates.zipWithIndex) yield {
      val (datum, datumIndex) = candidate
      val channel: String     = channels(candidateIndex)
      tupleSpace.get(singleton(channel)).foreach((subspace: Subspace[A]) => ignore { subspace.removeDataAtIndex(datumIndex) })
      datum
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
      k(consumeProducts(channels, chosenCandidates))
    } else {
      storeWaitingContinuation(channels, patterns, k)
    }
  }

  def fetchProduceCandidates(candidateChannels: List[Channel],
                             channelPosition: Int,
                             productPatterns: Seq[Pattern]): List[ProduceCandidate] =
    candidateChannels.zipWithIndex[Channel, List[(Channel, Int)]].flatMap {
      case (candidateChannel, candidateChannelIndex) =>
        if (channelPosition === candidateChannelIndex)
          singleton((candidateChannel, -1))
        else
          Nil
          // tupleSpace
          //   .get(singleton(candidateChannel))
          //   .map { (subspace: Subspace[A]) =>
          //     subspace.data
          //       .zipWithIndex[A, List[(A, Int)]]
          //       .filter { case (datum, _) => productPatterns.lift(candidateChannelIndex).exists(_.isMatch(datum)) }
          //       .map { case (_, datumIndex) => (candidateChannel, datumIndex) }
          //   }
          //   .toList
          //   .flatten
    }

  def matchCont(waitingK: WaitingContinuation[A], candidateChannelPosition: Int, channel: String): Boolean =
    waitingK.patterns.lift(candidateChannelPosition).exists(_.isMatch(channel))

  def extractConsumeCandidates(keyCandidates: List[List[Channel]],
                               channel: String): List[(List[ProduceCandidate], (List[Channel], Int))] = {
    for {
      candidateChannel         <- keyCandidates
      candidateChannelPosition <- candidateChannel.indexOf(channel).pure[List]
      subspace                 <- tupleSpace.get(candidateChannel).toList
      (k, ki)                  <- subspace.waitingContinuations.zipWithIndex if matchCont(k, candidateChannelPosition, channel)
      produceCandidates = fetchProduceCandidates(candidateChannel, candidateChannelPosition, k.patterns) if produceCandidates.nonEmpty
    } yield {
      (produceCandidates, (candidateChannel, ki))
    }
  }

  def consumeContinuation(chosenCandidate: (List[ProduceCandidate], (List[Channel], Int)),
                          product: A): Option[(WaitingContinuation[A], List[A])] =
    chosenCandidate match {
      case (produceCandidates, (candidateChannelKey, waitingContinuationIndex)) =>
        val products: List[A] = produceCandidates.flatMap {
          case (_, dataIndex) if dataIndex === -1 =>
            Some(product).toList
          case (produceChannel, dataIndex) =>
            tupleSpace.get(singleton(produceChannel)).flatMap(s => s.removeDataAtIndex(dataIndex)).toList
        }
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

  def produce(channel: Channel, product: A): Unit =
    ignore {
      val cands = tupleSpace.keys.foldLeft(List.empty[List[Channel]]) { (acc: List[List[Channel]], key: List[Channel]) =>
        if (key.exists(_.contains(channel))) key :: acc else acc
      }
      val consumers = extractConsumeCandidates(cands, channel)
      if (consumers.nonEmpty) {
        consumers.foreach { consumer =>
          consumeContinuation(consumer, product) match {
            case Some((consumedK, products)) =>
              consumedK.k(products)
            case None =>
              storeProduct(channel, product)
          }
        }
      } else {
        storeProduct(channel, product)
      }

    }
}
