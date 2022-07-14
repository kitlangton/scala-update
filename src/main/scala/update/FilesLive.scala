package update

import zio.nio.file.Path
import zio.stream._
import zio.{Chunk, IO, ZIO}

import java.io.IOException

final case class FilesLive() extends Files {

  override def allBuildSources(root: String): IO[IOException, Chunk[SourceFile]] = {
    val rootPath          = Path(root)
    val projectScalaPaths = FileUtils.allScalaFiles(rootPath / "project")
    val buildSbtPath      = ZStream.succeed(rootPath / "build.sbt")
    val buildMillPath     = FileUtils.allMillFiles(rootPath)
    val pluginsPath = ZStream
      .succeed(rootPath / "project" / "plugins.sbt")
    val sbtPropertiesFilePath = ZStream.succeed(rootPath / "project" / "build.properties")

    val allSourcePaths = (projectScalaPaths ++ buildSbtPath ++ pluginsPath ++ buildMillPath ++ sbtPropertiesFilePath)
      .filterZIO(path => zio.nio.file.Files.exists(path))

    allSourcePaths.mapZIO { path =>
      ZIO
        .readFile(path.toString)
        .map { content =>
          SourceFile(path, content, FileUtils.extension(path))
        }
    }.runCollect
  }

}
