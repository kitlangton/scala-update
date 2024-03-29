package update.model

import scala.math.Ordered.orderingToOrdered

enum Version:
  case SemVer(major: Int, minor: Int, patch: Int, preRelease: Option[PreRelease])
  case Other(value: String)

  def majorVersion: Option[Int] = this match
    case SemVer(major, _, _, _) => Some(major)
    case Other(_)               => None

  override def toString: String = this match
    case SemVer(major, minor, patch, preRelease) =>
      val preReleaseStr = preRelease.fold("")(pr => s"-$pr")
      s"$major.$minor.$patch$preReleaseStr"
    case Other(value) => value

  def isPreRelease: Boolean = this match
    case SemVer(_, _, _, Some(_)) => true
    case Other(_)                 => true
    case _                        => false

object Version:

  object MatchInt:
    def unapply(value: String): Option[Int] =
      value.toIntOption

  object MatchPreRelease:
    def unapply(value: String): Option[PreRelease] =
      PreRelease.parse(value)

  def apply(string: String): Version =
    string match
      // 1.1.1
      case s"${MatchInt(major)}.${MatchInt(minor)}.${MatchInt(patch)}" =>
        SemVer(major, minor, patch, None)

      // 1.1.1-RC1
      case s"${MatchInt(major)}.${MatchInt(minor)}.${MatchInt(patch)}-${MatchPreRelease(preRelease)}" =>
        SemVer(major, minor, patch, Some(preRelease))

      // 1.1
      case s"${MatchInt(major)}.${MatchInt(minor)}" =>
        SemVer(major, minor, 0, None)

      // 2.0-RC1
      case s"${MatchInt(major)}.${MatchInt(minor)}-${MatchPreRelease(preRelease)}" =>
        SemVer(major, minor, 0, Some(preRelease))

      case other =>
        Other(other)

  // None should be considered greater than any pre-release
  given Ordering[Option[PreRelease]] =
    new Ordering[Option[PreRelease]]:
      def compare(x: Option[PreRelease], y: Option[PreRelease]): Int =
        (x, y) match
          case (Some(_), None)        => -1
          case (None, Some(_))        => 1
          case (Some(pr1), Some(pr2)) => pr1 compare pr2
          case (None, None)           => 0

  given Ordering[Version] with
    def compare(x: Version, y: Version): Int =
      (x, y) match
        case (SemVer(m1, n1, p1, pr1), SemVer(m2, n2, p2, pr2)) =>
          summon[Ordering[(Int, Int, Int, Option[PreRelease])]]
            .compare((m1, n1, p1, pr1), (m2, n2, p2, pr2))
        case (SemVer(_, _, _, _), Other(_)) => -1
        case (Other(_), SemVer(_, _, _, _)) => 1
        case (Other(x), Other(y))           => x compare y
