package update.model

import scala.math.Ordered.orderingToOrdered

enum PreRelease:
  case RC(n: Int)            extends PreRelease
  case M(n: Int)             extends PreRelease
  case Alpha(n: Option[Int]) extends PreRelease
  case Beta(n: Option[Int])  extends PreRelease

  override def toString: String = this match
    case RC(n)          => s"RC$n"
    case M(n)           => s"M$n"
    case Alpha(Some(n)) => s"alpha.$n"
    case Alpha(None)    => "alpha"
    case Beta(Some(n))  => s"beta.$n"
    case Beta(None)     => "beta"

object PreRelease:
  import Version.MatchInt

  // alpha < beta < M < RC
  def compare(x: PreRelease, y: PreRelease): Int =
    def ordinal = (p: PreRelease) =>
      p match
        case PreRelease.RC(_)    => 4
        case PreRelease.M(_)     => 3
        case PreRelease.Beta(_)  => 2
        case PreRelease.Alpha(_) => 1

    def number(p: PreRelease): Int = p match
      case PreRelease.RC(n)          => n
      case PreRelease.M(n)           => n
      case PreRelease.Beta(Some(n))  => n
      case PreRelease.Alpha(Some(n)) => n
      case _                         => 0

    (ordinal(x), number(x)) compare (ordinal(y), number(y))

  def parse(value: String): Option[PreRelease] =
    val Re = raw"([A-Za-z]+)(\d+)(\w+)?".r
    value match
      case Re("RC", n, _)          => Some(RC(n.toInt))
      case Re("M", n, _)           => Some(M(n.toInt))
      case "alpha"                 => Some(Alpha(None))
      case s"alpha.${MatchInt(n)}" => Some(Alpha(Some(n)))
      case "beta"                  => Some(Beta(None))
      case s"beta.${MatchInt(n)}"  => Some(Beta(Some(n)))
      case _                       => None

  given ordering: Ordering[PreRelease] = (x: PreRelease, y: PreRelease) => PreRelease.compare(x, y)
