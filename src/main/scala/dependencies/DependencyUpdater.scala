package dependencies

import dependencies.versions.Versions
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

  def allUpdateOptions: IO[Throwable, List[(DependencyWithLocation, UpdateOptions)]] = for {
    pwd         <- System.property("user.dir").someOrFailException
    _           <- ZIO.fail(AppError.MissingBuildSbt).unlessZIO(zio.nio.file.Files.exists(Path(pwd) / "build.sbt"))
    sourceFiles <- files.allScalaFiles(pwd)
    deps         = DependencyParser.getDependencies(sourceFiles.toList)
    updates     <- ZIO.foreach(deps)(getUpgradeOptions)
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

  private def getUpgradeOptions(
    dependency: DependencyWithLocation
  ): IO[Throwable, (DependencyWithLocation, UpdateOptions)] =
    for {
      allVersions <- versions.getVersions(dependency.dependency.group, dependency.dependency.artifact)
    } yield dependency -> UpdateOptions.getOptions(dependency.dependency.version, allVersions)
}

object DependencyUpdater {
  val live: ZLayer[Versions with Files, Nothing, DependencyUpdater] =
    ZLayer.fromFunction(apply _)
}
