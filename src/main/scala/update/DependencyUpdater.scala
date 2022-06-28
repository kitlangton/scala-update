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

    isValidSbtProject <- zio.nio.file.Files.exists(Path(pwd) / "build.sbt")
    _                 <- ZIO.fail(AppError.MissingBuildSbt).unless(isValidSbtProject)

    sourceFiles <- files.allBuildSources(pwd)
    deps         = DependencyParser.getDependencies(sourceFiles)
    updates     <- ZIO.foreachPar(deps)(getUpdateOptions)
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
    dep: DependencyWithLocation
  ): IO[Throwable, (DependencyWithLocation, UpdateOptions)] =
    versions
      .getVersions(dep.dependency.group, dep.dependency.artifact)
      .map { allVersions =>
        dep -> UpdateOptions.getOptions(dep.dependency.version, allVersions)
      }
}

object DependencyUpdater {
  val live: ZLayer[Versions with Files, Nothing, DependencyUpdater] =
    ZLayer.fromFunction(apply _)
}
