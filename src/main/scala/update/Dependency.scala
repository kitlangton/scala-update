package update

final case class Group(value: String)    extends AnyVal
final case class Artifact(value: String) extends AnyVal

// major.minor.patch-prerelease
final case class Version(value: String) {
  lazy val details: VersionDetails =
    VersionDetails.fromString(value)

  def major: Int                 = details.major
  def minor: Int                 = details.minor
  def patch: Int                 = details.patch
  def preRelease: Option[String] = details.preRelease

  def isNewerThan(that: Version): Boolean =
    major > that.major ||
      (major == that.major && minor > that.minor) ||
      (major == that.major && minor == that.minor && patch > that.patch) ||
      (major == that.major && minor == that.minor && patch == that.patch && preRelease.isEmpty && that.preRelease.isDefined) ||
      (major == that.major && minor == that.minor && patch == that.patch && preRelease.isDefined && that.preRelease.isDefined && preRelease.get > that.preRelease.get)
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
    VersionDetails(major, minor, patch, preRelease)
  }
}

final case class VersionDetails(
  major: Int,
  minor: Int,
  patch: Int,
  preRelease: Option[String]
)
