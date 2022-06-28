package update.versions

import coursier.cache.FileCache
import coursier.{Module, ModuleName, Organization, Repositories}
import update.versions.ZioSyncInstance._
import update.{Artifact, DependencyUpdater, Files, Group, Version}
import zio._

final case class VersionsLive() extends Versions {
  private val cache = FileCache[Task]()

  // Currently hardcoded to Maven Central and scala 2.13
  override def getVersions(group: Group, artifact: Artifact): Task[List[Version]] =
    // Gets library versions from Maven Central
    getVersions(group, artifact, "2.13", None)
      .filterOrElse(_.nonEmpty) {
        // Gets plugin versions from Maven Central
        getVersions(group, artifact, "2.12", Some("1.0"))
      }
//      .orElseSucceed(List.empty)

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
        true
      )
      .run
      .map(_.left.map(new Error(_)))
      .absolve
      .map { case (versions, _) =>
        versions.available.map(Version)
      }
      .catchSome {
        case e if e.getMessage.contains("not found") =>
          ZIO.succeed(List.empty)
      }

}

//object VersionsDemo extends ZIOAppDefault {
//  val run = {
//    for {
//      _ <- VersionsLive().getVersions(Group("dev.zio"), Artifact("zio-json")).debug("Library Versions")
//      _ <- VersionsLive().getVersions(Group("com.github.sbt"), Artifact("sbt-native-packager")).debug("Plugin Versions")
//    } yield ()
//  }
//}
