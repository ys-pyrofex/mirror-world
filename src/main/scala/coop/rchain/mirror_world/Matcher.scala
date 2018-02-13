package coop.rchain.mirror_world

trait Matcher[A, B] {

  def isMatch(a: A, b: B): Boolean
}

object Matcher {

  implicit def patternTMatcher[T]: Matcher[Pattern, T] = _.isMatch(_)
}
