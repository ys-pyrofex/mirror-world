package coop.rchain.mirror_world

final case class WaitingContinuation[A](patterns: Seq[Pattern], k: Continuation[A])
