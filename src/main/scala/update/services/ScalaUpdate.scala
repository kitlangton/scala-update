package update.services

import update.model.*
import update.utils.Rewriter
import update.*
import update.services.dependencies.*
import zio.*

final case class ScalaUpdate(dependencyLoader: DependencyLoader, versions: Versions):

  // - Find all dependencies with their current versions
  // - Load all versions for each dependency
  // - Find the most recent patch/minor/major/pre-release version for each dependency
  // - THE USER will then select a version for each dependency (or not)
  // - Gather all selected versions with their source positions
  // - Group by source file
  // - Rewrite each source file with the new versions
  def updateAllDependencies(root: String): Task[List[WithVersions[WithSource[Dependency]]]] =
    for
      dependencies <- dependencyLoader.getDependencies(root)
      withVersions <- ZIO.foreachPar(dependencies)(getVersionsForDependency)
      _ <- ZIO.foreachDiscard(withVersions) { withVersions =>
             ZIO.succeed(
               println(
                 s"${withVersions.value.value} in ${withVersions.value.sourceInfo.path}\n versions: ${withVersions.versions}"
               )
             )
           }
      latestVersions = withVersions.flatMap(getLatestVersion).distinct
      _ <- ZIO.foreachDiscard(latestVersions) { version =>
             ZIO.succeed(println(s"${version.value} in ${version.sourceInfo.path}"))
           }
      _ <- writeToSource(latestVersions)
    yield withVersions

  private def writeToSource(selectedVersions: List[WithSource[Version]]): Task[Unit] =
    val groupedBySourceFile = selectedVersions.groupBy(_.sourceInfo.path)
    ZIO.foreachParDiscard(groupedBySourceFile) { case (path, versions) =>
      rewriteSourceFile(path, versions)
    }

  private def rewriteSourceFile(
      path: String,
      versions: List[WithSource[Version]]
  ): Task[Unit] =
    for
      sourceCode <- ZIO.readFile(path)
      patches = versions.map { version =>
                  Rewriter.Patch(
                    start = version.sourceInfo.start,
                    end = version.sourceInfo.end,
                    replacement = version.value.toString
                  )
                }
      updatedSourceCode = Rewriter.rewrite(sourceCode, patches)
      _                <- ZIO.writeFile(path, updatedSourceCode)
    yield ()

  private def getLatestVersion(withVersions: WithVersions[WithSource[Dependency]]): Option[WithSource[Version]] =
    withVersions.versions.filterNot(_.isPreRelease).maxOption.map { latest =>
      WithSource(latest, withVersions.value.sourceInfo)
    }

  private def getVersionsForDependency(
      dependency: WithSource[Dependency]
  ): Task[WithVersions[WithSource[Dependency]]] =
    for versions <- versions.getVersions(dependency.value)
    yield WithVersions(dependency, versions)

object ScalaUpdate:
  val layer = ZLayer.fromFunction(ScalaUpdate.apply)

  def updateAllDependencies(root: String): RIO[ScalaUpdate, List[WithVersions[WithSource[Dependency]]]] =
    ZIO.serviceWithZIO[ScalaUpdate](_.updateAllDependencies(root))
