package update.versions

import update.{Artifact, Dependency, Group, Version}
import zio._

trait Versions {
  def getVersions(group: Group, artifact: Artifact, sbtVersion: Option[Version]): Task[List[Version]]

  def getVersions(dependency: Dependency, sbtVersion: Option[Version]): Task[List[Version]] =
    getVersions(dependency.group, dependency.artifact, sbtVersion)
}

object Versions {

  def getVersions(
    group: Group,
    artifact: Artifact,
    sbtVersion: Option[Version]
  ): ZIO[Versions, Throwable, List[Version]] =
    ZIO.serviceWithZIO[Versions](_.getVersions(group, artifact, sbtVersion))

  def getVersions(dependency: Dependency, sbtVersion: Option[Version]): ZIO[Versions, Throwable, List[Version]] =
    ZIO.serviceWithZIO[Versions](_.getVersions(dependency, sbtVersion))

  val live: ULayer[Versions] =
    ZLayer.fromFunction(VersionsLive.apply _)

  def test(versions: Map[(Group, Artifact), List[Version]]): ULayer[Versions] =
    ZLayer.succeed {
      VersionsFake(versions)
    }
}

final case class VersionsFake(versions: Map[(Group, Artifact), List[Version]]) extends Versions {
  override def getVersions(group: Group, artifact: Artifact, sbtVersion: Option[Version]): Task[List[Version]] =
    ZIO.succeed(versions.getOrElse((group, artifact), Nil))
}
