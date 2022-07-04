package update.versions

import coursier.cache.FileCache
import coursier.{Module, ModuleName, Organization, Resolve}
import update.versions.ZioSyncInstance._
import update.{Artifact, Group, Version}
import zio._

final case class VersionsLive() extends Versions {
  private val cache   = FileCache[Task]()
  private val resolve = Resolve[Task](cache)

  // Currently hardcoded to Scala 2.13
  override def getVersions(group: Group, artifact: Artifact): Task[List[Version]] =
    // Gets library versions
    getVersions(group, artifact, "2.13", None)
      .filterOrElse(_.nonEmpty) {
        // Gets plugin versions
        getVersions(group, artifact, "2.12", Some("1.0"))
      }

  private def getVersions(
    group: Group,
    artifact: Artifact,
    scalaVersion: String,
    sbtVersion: Option[String]
  ): Task[List[Version]] =
    resolve.finalRepositories.flatMap { repositories =>
      ZIO
        .foreachPar(repositories) { repository =>
          repository
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
        .map(_.flatten.toList)
    }
}
