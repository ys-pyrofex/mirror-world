package coop.rchain.mirror_world

import scala.collection.mutable

trait StorageFixtures {

  def mm[C]: mutable.HashMap[C, mutable.Set[List[C]]] with mutable.MultiMap[C, List[C]]

  def withTestStorage[C, P, A, K](f: Storage[C, P, A, K] => Unit)(implicit sc: Serialize[C]): Unit = {
    val ns: Storage[C, P, A, K] = new Storage(Store.empty[C, P, A, K], mm[C])
    f(ns)
  }
}
