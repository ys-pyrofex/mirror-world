package coop.rchain.mirror_world

import scala.collection.mutable

final class Storage[C, P, A, K](val tuplespace: Store[C, P, A, K], val joinMap: mutable.MultiMap[C, List[C]])
