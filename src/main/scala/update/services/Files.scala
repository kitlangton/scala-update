package update.services

import update.utils.FileUtils
import zio.*
import zio.nio.file.Path
import zio.stream.*

trait Files:
  def allBuildScalaPaths(path: String): Task[Chunk[Path]]

object Files:
  val live = ZLayer.succeed(FilesLive)

case object FilesLive extends Files:
  override def allBuildScalaPaths(root: String): Task[Chunk[Path]] =
    val buildProperties = "build.properties"

    val rootPath = Path(root)
    // Build files with Sbt1 dialect content
    val projectScalaPaths = FileUtils.allScalaFiles(rootPath / "project")
    val buildSbtPath      = ZStream.succeed(rootPath / "build.sbt")
    val buildMillPath     = FileUtils.allMillFiles(rootPath)
    val pluginsPath       = ZStream.succeed(rootPath / "project" / "plugins.sbt")
    // A .properties file
    val sbtPropertiesFilePath = ZStream.succeed(rootPath / "project" / buildProperties)
    // ++ sbtPropertiesFilePath

    val allSourcePaths = (projectScalaPaths ++ buildSbtPath ++ pluginsPath ++ buildMillPath)
      .filterZIO(path => zio.nio.file.Files.exists(path))

    allSourcePaths.runCollect
