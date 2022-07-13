package update

import update.versions.Versions
import zio._
import zio.nio.file.Path

import java.io.IOException

case class DependencyUpdater(versions: Versions, files: Files) {

  def updateDependencies: IO[Throwable, Unit] =
    for {
      updates <- allUpdateOptions
      newestVersions = updates.flatMap { case (dep, options) =>
                         options.newestVersion.map(dep -> _)
                       }
      _ <- runUpdates(Chunk.from(newestVersions))
    } yield ()

  def runUpdates(updates: Chunk[(DependencyWithLocation, Version)]): IO[IOException, Unit] = {
    val collected = groupUpdatesByFile(updates)
    ZIO.foreachDiscard(collected) { case (path, updates) =>
      updateFile(path, updates)
    }

  }

  def allUpdateOptions: IO[Throwable, Chunk[(DependencyWithLocation, UpdateOptions)]] = for {
    pwd <- System.property("user.dir").someOrFailException

    sourceFiles <- files.allBuildSources(pwd)
    deps         = DependencyParser.getDependencies(sourceFiles)
    sbtVersion =
      deps
        .find(dwl => dwl.dependency.group == Dependency.sbtGroup && dwl.dependency.artifact == Dependency.sbtArtifact)
        .map(_.dependency.version)
    updates <- ZIO.foreachPar(deps)(dep => getUpdateOptions(dep, sbtVersion))
  } yield updates

  private def groupUpdatesByFile(
    updates: Chunk[(DependencyWithLocation, Version)]
  ): Map[Path, Chunk[VersionWithLocation]] =
    updates.groupMap(_._1.location.path) { case (dep, version) =>
      VersionWithLocation(version, dep.location)
    }

  private def updateFile(path: Path, updates: Chunk[VersionWithLocation]): IO[IOException, Unit] =
    ZIO.scoped {
      for {
        oldContent <- ZIO.readFile(path.toString)
        replacements = updates.map { case VersionWithLocation(version, location) =>
                         Replacement(location.start, location.end, "\"" + version.value + "\"")
                       }
        newContent = Replacement.replace(oldContent, replacements.toList)
        _         <- ZIO.writeFile(path.toString, newContent)
      } yield ()
    }

  private def getUpdateOptions(
    dep: DependencyWithLocation,
    sbtVersion: Option[Version]
  ): IO[Throwable, (DependencyWithLocation, UpdateOptions)] =
    versions
      .getVersions(dep.dependency.group, dep.dependency.artifact, sbtVersion)
      .map { allVersions =>
        dep -> UpdateOptions.getOptions(dep.dependency.version, allVersions)
      }
}

object DependencyUpdater {
  val live: ZLayer[Versions with Files, Nothing, DependencyUpdater] =
    ZLayer.fromFunction(apply _)
}
