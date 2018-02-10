package coop.rchain.mirror_world

sealed trait Pattern extends Product with Serializable {

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  def isMatch(a: Any): Boolean =
    this match {
      case Wildcard           => true
      case StringMatch(value) => value == a
    }
}
final case class StringMatch(value: String) extends Pattern
case object Wildcard                        extends Pattern
