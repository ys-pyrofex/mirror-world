package coop.rchain.mirror_world

final case class WaitingContinuation[K](patterns: Seq[Pattern], k: K)
