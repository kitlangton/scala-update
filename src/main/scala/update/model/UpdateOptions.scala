package update.model
import Ordered.given

enum VersionType:
  case Major
  case Minor
  case Patch
  case PreRelease

  // wrap around
  def next: VersionType = VersionType.fromOrdinal((ordinal + 1) % VersionType.values.length)
  def prev: VersionType = VersionType.fromOrdinal((ordinal - 1 + VersionType.values.length) % VersionType.values.length)

final case class UpdateOptions(
    major: Option[Version],
    minor: Option[Version],
    patch: Option[Version],
    preRelease: Option[Version]
):

  def hasMajor: Boolean      = major.isDefined
  def hasMinor: Boolean      = minor.isDefined
  def hasPatch: Boolean      = patch.isDefined
  def hasPreRelease: Boolean = preRelease.isDefined
  def hasVersionType(versionType: VersionType): Boolean =
    versionType match
      case VersionType.Major      => hasMajor
      case VersionType.Minor      => hasMinor
      case VersionType.Patch      => hasPatch
      case VersionType.PreRelease => hasPreRelease

  def newestVersion: Option[Version] =
    major.orElse(minor).orElse(patch).orElse(preRelease)

  def allVersions: List[Version] = major.toList ++ minor.toList ++ patch.toList ++ preRelease.toList

  def isEmpty: Boolean = major.isEmpty && minor.isEmpty && patch.isEmpty && preRelease.isEmpty

  def isNonEmpty: Boolean = !isEmpty

object UpdateOptions:

  def getOptions(current: Version, available0: List[Version]): UpdateOptions =
    current match
      case v: Version.SemVer => getOptions(v, available0)
      case _                 => UpdateOptions(None, None, None, None)

  def getOptions(current: Version.SemVer, available0: List[Version]): UpdateOptions =
    val available = available0.collect { case v: Version.SemVer => v }
    val major     = current.major
    val minor     = current.minor
    val patch     = current.patch

    val allNewerVersions = available.filter(_ > current).sorted

    val majorVersion = allNewerVersions
      .filter(v => ((current.preRelease.isDefined && v.major == major) || v.major > major) && v.preRelease.isEmpty)
      .lastOption

    val minorVersion = allNewerVersions
      .filter(v => v.major == major && v.minor > minor && v.preRelease.isEmpty)
      .lastOption
      .filterNot(majorVersion.contains)

    val patchVersion = allNewerVersions
      .filter(v => v.major == major && v.minor == minor && v.patch > patch && v.preRelease.isEmpty)
      .lastOption
      .filterNot(v => majorVersion.contains(v) || minorVersion.contains(v))

    val preReleaseVersions =
      allNewerVersions
        .filter(_.preRelease.isDefined)
        .filterNot { version =>
          patchVersion.exists(_ >= version) ||
          minorVersion.exists(_ >= version) ||
          majorVersion.exists(_ >= version)
        }
        .lastOption

    UpdateOptions(majorVersion, minorVersion, patchVersion, preReleaseVersions)
