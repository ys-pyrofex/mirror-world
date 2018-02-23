package coop.rchain.mirror_world

trait Matcher[A, B] {

  def isMatch(a: A, b: B): Option[B]
}

object Matcher {

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  implicit object stringMatcher extends Matcher[Pattern, String] {
    def isMatch(a: Pattern, b: String): Option[String] = Some(b).filter(a.isMatch)
  }
}
