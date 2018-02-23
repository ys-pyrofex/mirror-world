package coop.rchain.mirror_world

trait Matcher[A, B] {

  def isMatch(a: A, b: B): Option[B]
}

object Matcher {

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  implicit val stringMatcher: Matcher[Pattern, String] = (a: Pattern, b: String) => Some(b).filter(a.isMatch)
}
