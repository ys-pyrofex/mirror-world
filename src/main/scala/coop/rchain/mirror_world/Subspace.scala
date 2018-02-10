package coop.rchain.mirror_world

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
class Subspace[A, K](_data: mutable.ListBuffer[A], _waitingContinuations: mutable.ListBuffer[WaitingContinuation[K]]) {

  def removeDataAtIndex(index: Int): Option[A] = _data.lift(index).map { (a: A) =>
    ignore { _data.remove(index) }
    a
  }

  def removeWaitingContinuationAtIndex(index: Int): Option[WaitingContinuation[K]] =
    _waitingContinuations.lift(index).map { (k: WaitingContinuation[K]) =>
      ignore { _waitingContinuations.remove(index) }
      k
    }

  def appendData(product: A): Subspace[A, K] = {
    ignore { _data.+=:(product) }
    this
  }

  def appendWaitingContinuation(k: WaitingContinuation[K]): Subspace[A, K] = {
    ignore { _waitingContinuations.+=:(k) }
    this
  }

  def data: Seq[A] = _data.toList

  def waitingContinuations: Seq[WaitingContinuation[K]] = _waitingContinuations.toList
}

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
object Subspace {

  def empty[A, K]: Subspace[A, K] =
    new Subspace(mutable.ListBuffer.empty[A], mutable.ListBuffer.empty[WaitingContinuation[K]])
}
