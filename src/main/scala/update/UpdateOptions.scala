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

  def isEmpty: Boolean = major.isEmpty && minor.isEmpty && patch.isEmpty && preRelease.isEmpty

  def isNonEmpty: Boolean = !isEmpty
}

object UpdateOptions {

  def getOptions(current: Version, available: List[Version]): UpdateOptions = {
    val major = current.major
    val minor = current.minor
    val patch = current.patch

    val allNewerVersions =
      available.filter(_.isNewerThan(current)).sorted
    val majorVersion =
      allNewerVersions
        .filter(v => ((current.preRelease.isDefined && v.major == major) || v.major > major) && v.preRelease.isEmpty)
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
        // TODO: Filter out if the newest major version is newer than the pre-release version
        .filterNot(v => majorVersion.exists(_.isNewerThan(v)))
        .lastOption

    UpdateOptions(majorVersion, minorVersion, patchVersion, preReleaseVersions)
  }

}
