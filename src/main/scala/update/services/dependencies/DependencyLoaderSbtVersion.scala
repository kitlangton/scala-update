package update.services.dependencies

import update.model.*
import zio.*

final case class DependencyLoaderSbtVersion() extends DependencyLoader:
  private val regex = """sbt.version\s*=\s*([\d\.]+)""".r

  // look in the project/build.properties file for the sbt version
  def getDependencies(root: String): Task[List[WithSource[Dependency]]] = {
    for
      buildProperties <- ZIO
                           .readFile(root + "/project/build.properties")
                           .option
                           .some
      versionMatch <- ZIO.fromOption(regex.findFirstMatchIn(buildProperties))
      sourceInfo = SourceInfo(
                     root + "/project/build.properties",
                     versionMatch.start(1),
                     versionMatch.end(1)
                   )
      version = Version(versionMatch.group(1))
    yield WithSource(Dependency(Group("org.scala-sbt"), Artifact("sbt"), version, true), sourceInfo)
  }.unsome.map(_.toList)

object DependencyLoaderSbtVersion:
  val layer = ZLayer.succeed(DependencyLoaderSbtVersion())
