package update.versions

import update.{Artifact, Dependency, Group, Version}
import zio._

trait Versions {
  def getVersions(group: Group, artifact: Artifact): Task[List[Version]]

  def getVersions(dependency: Dependency): Task[List[Version]] =
    getVersions(dependency.group, dependency.artifact)
}

object Versions {

  def getVersions(group: Group, artifact: Artifact): ZIO[Versions, Throwable, List[Version]] =
    ZIO.serviceWithZIO[Versions](_.getVersions(group, artifact))

  def getVersions(dependency: Dependency): ZIO[Versions, Throwable, List[Version]] =
    ZIO.serviceWithZIO[Versions](_.getVersions(dependency))

  val live: ULayer[Versions] =
    ZLayer.fromFunction(VersionsLive.apply _)

  def test(versions: Map[(Group, Artifact), List[Version]]): ULayer[Versions] =
    ZLayer.succeed {
      VersionsFake(versions)
    }
}

final case class VersionsFake(versions: Map[(Group, Artifact), List[Version]]) extends Versions {
  override def getVersions(group: Group, artifact: Artifact): Task[List[Version]] =
    ZIO.succeed(versions.getOrElse((group, artifact), Nil))
}
