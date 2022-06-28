package update

final case class UpdateOptions(
  major: Option[Version],
  minor: Option[Version],
  patch: Option[Version],
  preRelease: Option[Version]
) {
  def newestVersion: Option[Version] =
    major.orElse(minor).orElse(patch).orElse(preRelease)

  def allVersions: List[Version] = major.toList ++ minor.toList ++ patch.toList ++ preRelease.toList

  def isEmpty: Boolean    = major.isEmpty && minor.isEmpty && patch.isEmpty && preRelease.isEmpty
  def isNonEmpty: Boolean = !isEmpty
}

object UpdateOptions {

  def getOptions(version: Version, versions: List[Version]): UpdateOptions = {
    val major = version.major
    val minor = version.minor
    val patch = version.patch

    val allNewerVersions =
      versions.filter(_.isNewerThan(version))
    val majorVersion =
      allNewerVersions
        .filter(v => ((version.preRelease.isDefined && v.major == major) || v.major > major) && v.preRelease.isEmpty)
        .lastOption
    val minorVersion =
      allNewerVersions
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
        .lastOption

    UpdateOptions(majorVersion, minorVersion, patchVersion, preReleaseVersions)
  }

}
