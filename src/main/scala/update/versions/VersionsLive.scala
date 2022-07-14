package update.versions

import coursier.cache.FileCache
import coursier.{Module, ModuleName, Organization, Resolve}
import update.versions.ZioSyncInstance._
import update.{Artifact, Dependency, Group, Version}
import zio._

final case class VersionsLive() extends Versions {
  private val cache   = FileCache[Task]()
  private val resolve = Resolve[Task](cache)

  // Currently hardcoded to Scala 2.13
  override def getVersions(group: Group, artifact: Artifact, sbtVersion: Option[Version]): Task[List[Version]] =
    // Gets library versions
    getVersions(group, artifact, "2.13", None)
      .filterOrElse(_.nonEmpty) {
        // Gets plugin versions
        getVersions(group, artifact, "2.12", sbtVersion.map(_.value))
      }

  private def getVersions(
    group: Group,
    artifact: Artifact,
    scalaVersion: String,
    sbtVersion: Option[String]
  ): Task[List[Version]] = {
    val isSbt = Dependency.isSbt(group, artifact)

    resolve.finalRepositories.flatMap { repositories =>
      ZIO
        .foreachPar(repositories) { repository =>
          val attributes =
            if (isSbt)
              Map.empty[String, String]
            else
              Map("scalaVersion" -> scalaVersion) ++ sbtVersion.map("sbtVersion" -> _)

          repository
            .versions(
              Module(
                Organization(group.value),
                ModuleName(artifact.value),
                attributes
              ),
              cache.fetch,
              versionsCheckHasModule = !isSbt
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
}
