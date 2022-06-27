package dependencies.versions

import coursier.cache.FileCache
import coursier.{Module, ModuleName, Organization, Repositories}
import dependencies.versions.ZioSyncInstance._
import dependencies.{Artifact, DependencyUpdater, Files, Group, Version}
import zio._

final case class VersionsLive() extends Versions {
  private val cache = FileCache[Task]()

  // TODO: Currently hardcoded to Maven Central and scala 2.13
  override def getVersions(group: Group, artifact: Artifact): Task[List[Version]] =
    Repositories.central
      .versions(Module(Organization(group.value), ModuleName(artifact.value + s"_2.13")), cache.fetch)
      .run
      .map(_.left.map(new Error(_)))
      .absolve
      .map { case (versions, _) =>
        versions.available.map(Version)
      }
      .orElseSucceed(List.empty)
}

//object VersionsDemo extends ZIOAppDefault {
//  val run =
//    ZIO
//      .serviceWithZIO[DependencyUpdater](_.allUpdateOptions)
//      .provide(DependencyUpdater.live, Files.live, Versions.live)
//}
