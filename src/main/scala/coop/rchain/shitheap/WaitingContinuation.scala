package coop.rchain.shitheap

final case class WaitingContinuation[A](patterns: List[Pattern], context: (Code[A], Env[A], Persistent))
