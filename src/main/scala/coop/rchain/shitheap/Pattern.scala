package coop.rchain.shitheap

@SuppressWarnings(Array("org.wartremover.warts.Equals"))
class Pattern(pattern: String) {
  def isMatch(a: Any): Boolean =
    this match {
      case Wildcard => true
      case _        => this.pattern == a
    }
}

case object Wildcard extends Pattern("")
