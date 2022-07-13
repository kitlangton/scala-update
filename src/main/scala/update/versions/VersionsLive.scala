package update.versions

import coursier.cache.FileCache
import coursier.{Module, ModuleName, Organization, Repositories}
import update.versions.ZioSyncInstance._
import update.{Artifact, Group, Version}
import zio._

final case class VersionsLive() extends Versions {
  private val cache = FileCache[Task]()

  // Currently hardcoded to Maven Central and scala 2.13
  override def getVersions(group: Group, artifact: Artifact, sbtVersion: Option[Version]): Task[List[Version]] =
    // Gets library versions from Maven Central
    getVersions(group, artifact, "2.13", None)
      .filterOrElse(_.nonEmpty) {
        // Gets plugin versions from Maven Central
        getVersions(group, artifact, "2.12", sbtVersion.map(_.value))
      }

  private def getVersions(
    group: Group,
    artifact: Artifact,
    scalaVersion: String,
    sbtVersion: Option[String]
  ): Task[List[Version]] =
    Repositories.central
      .versions(
        Module(
          Organization(group.value),
          ModuleName(artifact.value),
          Map("scalaVersion" -> scalaVersion) ++ sbtVersion.map("sbtVersion" -> _)
        ),
        cache.fetch,
        versionsCheckHasModule = true
      )
      .run
      .map(_.left.map(new Error(_)))
      .absolve
      .map { case (versions, _) =>
        versions.available.map(Version(_))
      }
      .catchSome {
        case e if e.getMessage.contains("not found") =>
          ZIO.succeed(List.empty)
      }

}
