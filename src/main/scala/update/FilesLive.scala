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
    val pluginsPath = ZStream
      .succeed(rootPath / "project" / "plugins.sbt")
      .filterZIO(path => zio.nio.file.Files.exists(path))

    val allSourcePaths = projectScalaPaths ++ buildSbtPath ++ pluginsPath

    allSourcePaths.mapZIO { path =>
      ZIO
        .readFile(path.toString)
        .map { content =>
          SourceFile(path, content)
        }
    }.runCollect
  }

}
