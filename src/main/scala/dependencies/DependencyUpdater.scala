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
      collected = groupUpdatesByFile(newestVersions)
      _ <- ZIO.foreachDiscard(collected) { case (path, updates) =>
             updateFile(path, updates)
           }
    } yield ()

  def allUpdateOptions: IO[Throwable, List[(DependencyWithLocation, UpgradeOptions)]] = for {
    pwd         <- System.property("user.dir").someOrFailException
    sourceFiles <- files.allScalaFiles(pwd)
    deps         = DependencyParser.getDependencies(sourceFiles.toList)
    updates     <- ZIO.foreach(deps)(getUpgradeOptions)
  } yield updates

  private def groupUpdatesByFile(
    updates: List[(DependencyWithLocation, Version)]
  ): Map[Path, List[VersionWithLocation]] =
    updates.groupMap(_._1.location.path) { case (dep, version) =>
      VersionWithLocation(version, dep.location)
    }

  private def updateFile(path: Path, updates: List[VersionWithLocation]): IO[IOException, Unit] =
    ZIO.scoped {
      for {
        oldContent <- ZIO.readFile(path.toString)
        replacements = updates.map { case VersionWithLocation(version, location) =>
                         Replacement(location.start, location.end, "\"" + version.value + "\"")
                       }
        newContent = Replacement.replace(oldContent, replacements)
        _         <- ZIO.writeFile(path.toString, newContent)
      } yield ()
    }

  private def getUpgradeOptions(
    dependency: DependencyWithLocation
  ): IO[Throwable, (DependencyWithLocation, UpgradeOptions)] =
    for {
      allVersions <- versions.getVersions(dependency.dependency.group, dependency.dependency.artifact)
    } yield dependency -> UpgradeOptions.getOptions(dependency.dependency.version, allVersions)
}

object DependencyUpdater {
  val live: ZLayer[Versions with Files, Nothing, DependencyUpdater] =
    ZLayer.fromFunction(apply _)
}
