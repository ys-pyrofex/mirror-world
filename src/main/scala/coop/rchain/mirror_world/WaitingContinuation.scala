package coop.rchain.mirror_world

final case class WaitingContinuation[A](patterns: List[Pattern], k: Continuation[A])
