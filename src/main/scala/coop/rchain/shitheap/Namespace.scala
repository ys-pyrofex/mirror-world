package coop.rchain.shitheap

import cats.implicits._

class Namespace[A](t: Tuplespace[A]) {

  def extractDataCandidates(channels: List[Channel], patterns: List[Pattern]): List[ConsumeCandidate[A]] =
    channels.zipWithIndex.flatMap {
      case (channel, channelIndex) =>
        t.get(List(channel))
          .map { (subspace: Subspace[A]) =>
            subspace.data.zipWithIndex
              .filter {
                case (datum, _) => patterns(channelIndex).isMatch(datum)
              }
          }
          .toList
          .flatten
    }

  def consumeProducts(channels: List[Channel], chosenCandidates: List[ConsumeCandidate[A]]): List[A] = {
    for ((candidate, candidateIndex) <- chosenCandidates.zipWithIndex) yield {
      val (datum, datumIndex) = candidate
      val channel: String     = channels(candidateIndex)
      t.get(List(channel)).foreach((subspace: Subspace[A]) => ignore { subspace.removeDataAtIndex(datumIndex) })
      datum
    }
  }

  def storeWaitingContinuation(channels: List[Channel],
                               patterns: List[Pattern],
                               code: Code[A],
                               env: Env[A],
                               persistent: Persistent): Unit = {
    val waitingContinuation: WaitingContinuation[A] = WaitingContinuation[A](patterns, (code, env, persistent))
    t.get(channels) match {
      case Some(x) =>
        ignore { x.appendWaitingContinuation(waitingContinuation) }
      case None =>
        ignore { t.put(channels, Subspace.empty[A].appendWaitingContinuation(waitingContinuation)) }
    }
  }

  def consume(channels: List[Channel], patterns: List[Pattern], code: Code[A], env: Env[A], persistent: Persistent): Unit = {
    val chosenCandidates = extractDataCandidates(channels, patterns)
    if (chosenCandidates.nonEmpty) {
      code(env, consumeProducts(channels, chosenCandidates))
    } else {
      storeWaitingContinuation(channels, patterns, code, env, persistent)
    }
  }

  def fetchProduceCandidates(candidateChannels: List[Channel],
                             channelPosition: Int,
                             productPatterns: Seq[Pattern]): List[ProduceCandidate] =
    candidateChannels.zipWithIndex[Channel, List[(Channel, Int)]].flatMap {
      case (candidateChannel, candidateChannelIndex) =>
        if (channelPosition === candidateChannelIndex)
          List((candidateChannel, -1))
        else
          t.get(List(candidateChannel))
            .map { (subspace: Subspace[A]) =>
              subspace.data
                .zipWithIndex[A, List[(A, Int)]]
                .filter { case (datum, _) => productPatterns(candidateChannelIndex).isMatch(datum) }
                .map { case (_, datumIndex) => (candidateChannel, datumIndex) }
            }
            .toList
            .flatten
    }


  def extractConsumeCandidates(candidateChannelsKey: List[List[Channel]],
                               channel: String,
                               product: Any): List[(List[ProduceCandidate], (List[Channel], Int))] = {
    for {
      candidateChannelKey <- candidateChannelsKey
      candidateChannel = candidateChannelKey
      channelPosition  = candidateChannel.indexOf(channel)
      subspace                        <- t.get(candidateChannelKey).toList
      (waitingCont, waitingContIndex) <- subspace.waitingContinuations.zipWithIndex
      if reachAroundGet(waitingCont.patterns, channelPosition).exists(_.isMatch(product))
      produceCandidates = fetchProduceCandidates(candidateChannelKey, channelPosition, waitingCont.patterns)
      if produceCandidates.nonEmpty
    } yield {
      (produceCandidates, (candidateChannelKey, waitingContIndex))
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
            t.get(List(produceChannel)).flatMap(s => s.removeDataAtIndex(dataIndex)).toList
        }
        t.get(candidateChannelKey)
          .flatMap(_.removeWaitingContinuationAtIndex(waitingContinuationIndex))
          .map((value: WaitingContinuation[A]) => (value, products))
    }

  def storeProduct(channel: Channel, product: A): Unit =
    t.get(List(channel)) match {
      case Some(s) =>
        ignore { s.appendData(product) }
      case None =>
        ignore { t.put(List(channel), Subspace.empty[A].appendData(product)) }
    }

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  def produce(channel: Channel, product: A): Unit = {
    var candidateChannelsKey = List.empty[List[Channel]]
    for (key <- t.keys) {
      if (key.exists(_.contains(channel)))
        candidateChannelsKey = candidateChannelsKey ++ List(key)
      val candidates = extractConsumeCandidates(candidateChannelsKey, channel, product)
      candidates.headOption match {
        case Some(chosenCandidate) =>
          for {
            (consumedK, products) <- consumeContinuation(chosenCandidate, product)
          } {
            val (code, env, _) = consumedK.context
            code(env, products)
          }
        case None =>
          storeProduct(channel, product)
      }
    }
  }
}
