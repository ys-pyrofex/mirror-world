package coop.rchain.mirror_world

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
class Subspace[A](_data: mutable.ListBuffer[A], _waitingContinuations: mutable.ListBuffer[WaitingContinuation[A]]) {

  def removeDataAtIndex(index: Int): Option[A] = _data.lift(index).map { (a: A) =>
    ignore { _data.remove(index) }
    a
  }

  def removeWaitingContinuationAtIndex(index: Int): Option[WaitingContinuation[A]] =
    _waitingContinuations.lift(index).map { (k: WaitingContinuation[A]) =>
      ignore { _waitingContinuations.remove(index) }
      k
    }

  def appendData(product: A): Subspace[A] = {
    ignore { _data.+=:(product) }
    this
  }

  def appendWaitingContinuation(k: WaitingContinuation[A]): Subspace[A] = {
    ignore { _waitingContinuations.+=:(k) }
    this
  }

  def data: List[A] = _data.toList

  def waitingContinuations: List[WaitingContinuation[A]] = _waitingContinuations.toList
}

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
object Subspace {

  def empty[A]: Subspace[A] =
    new Subspace[A](mutable.ListBuffer.empty[A], mutable.ListBuffer.empty[WaitingContinuation[A]])
}
