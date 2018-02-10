package coop.rchain.mirror_world

sealed abstract class Pattern extends Product with Serializable {
  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  def isMatch(a: Any): Boolean =
    this match {
      case Wildcard            => true
      case StringMatch(string) => string == a
    }
}
final case class StringMatch(string: String) extends Pattern
case object Wildcard                         extends Pattern
