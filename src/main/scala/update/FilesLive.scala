package update

import zio.nio.file.Path
import zio.stream._
import zio.{Chunk, IO, ZIO}

import java.io.IOException

case object FilesLive extends Files {
  override def allBuildSources(root: String): IO[IOException, Chunk[SourceFile]] = {
    val buildProperties = "build.properties"

    val rootPath = Path(root)
    // Build files with Sbt1 dialect content
    val projectScalaPaths = FileUtils.allScalaFiles(rootPath / "project")
    val buildSbtPath = ZStream.succeed(rootPath / "build.sbt")
    val buildMillPath = FileUtils.allMillFiles(rootPath)
    val pluginsPath = ZStream.succeed(rootPath / "project" / "plugins.sbt")
    // A .properties file
    val sbtPropertiesFilePath = ZStream.succeed(rootPath / "project" / buildProperties)

    val allSourcePaths = (projectScalaPaths ++ buildSbtPath ++ pluginsPath ++ buildMillPath ++ sbtPropertiesFilePath)
      .filterZIO(path => zio.nio.file.Files.exists(path))

    allSourcePaths.mapZIO { path =>
      ZIO
        .readFile(path.toString)
        .map { content =>
          {
            if (path.filename.toString == buildProperties)
              SourceFile.BuildPropertiesSourceFile
            else
              SourceFile.Sbt1DialectSourceFile
          }.tupled(path, content)
        }
    }.runCollect
  }

}
