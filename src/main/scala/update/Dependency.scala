package update

final case class Group(value: String)    extends AnyVal
final case class Artifact(value: String) extends AnyVal

final case class PreRelease(value: String) extends AnyVal {
  override def toString: String = value
}

object PreRelease {

  implicit val ordering: Ordering[PreRelease] = new Ordering[PreRelease] {
    private val Re = raw"([A-Za-z]+)(\d+)(\w+)?".r
    override def compare(x: PreRelease, y: PreRelease): Int =
      x.value match {
        case Re("RC", n, _) =>
          y.value match {
            case Re("RC", m, _) => n.toInt compare m.toInt
            case _              => 1
          }
        case Re("M", n, _) =>
          y.value match {
            case Re("RC", _, _) => -1
            case Re("M", m, _)  => n.toInt compare m.toInt
            case _              => 1
          }
        case _ => -1
      }
  }
  implicit val ordered = Ordered.orderingToOrdered[PreRelease] _
}

// major.minor.patch-prerelease
final case class Version(value: String) {
  lazy val details: VersionDetails =
    VersionDetails.fromString(value)

  def major: Int                     = details.major
  def minor: Int                     = details.minor
  def patch: Int                     = details.patch
  def preRelease: Option[PreRelease] = details.preRelease

  def isNewerThan(that: Version): Boolean =
    major > that.major ||
      (major == that.major && minor > that.minor) ||
      (major == that.major && minor == that.minor && patch > that.patch) ||
      (major == that.major && minor == that.minor && patch == that.patch && preRelease.isEmpty && that.preRelease.isDefined) ||
      (major == that.major && minor == that.minor && patch == that.patch && preRelease.isDefined && that.preRelease.isDefined && preRelease.get > that.preRelease.get)

}

object Version {
  implicit val ordering: Ordering[Version] =
    Ordering.by[Version, (Int, Int, Int, Option[PreRelease])](v => (v.major, v.minor, v.patch, v.preRelease))
}

// group %% artifact % version
final case class Dependency(group: Group, artifact: Artifact, version: Version)

object Dependency {
  implicit val dependencyOrder: Ordering[Dependency] =
    Ordering.by(d => (d.group.value, d.artifact.value, d.version.value))
}

object VersionDetails {
  def fromString(string: String): VersionDetails = {
    val parts      = string.split("[.-]")
    val major      = parts.lift(0).flatMap(_.toIntOption).getOrElse(0)
    val minor      = parts.lift(1).flatMap(_.toIntOption).getOrElse(0)
    val patch      = parts.lift(2).flatMap(_.toIntOption).getOrElse(0)
    val preRelease = parts.lift(3)
    VersionDetails(major, minor, patch, preRelease.map(PreRelease(_)))
  }
}

final case class VersionDetails(
  major: Int,
  minor: Int,
  patch: Int,
  preRelease: Option[PreRelease]
)
