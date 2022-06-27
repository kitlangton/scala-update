package dependencies

import zio.nio.file.Path
import zio.stream.ZStream
import zio.{Chunk, IO, ZIO}

import java.io.IOException

final case class FilesLive() extends Files {

  override def allScalaFiles(root: String): IO[IOException, Chunk[SourceFile]] = {
    val rootPath = Path(root)
    for {
      files <- FileUtils
                 .allScalaFiles(rootPath / "project")
                 .concat(ZStream.succeed(rootPath / "build.sbt"))
                 .concat(ZStream.succeed(rootPath / "project" / "plugins.sbt"))
                 .mapZIO { path =>
                   ZIO
                     .readFile(path.toString)
                     .map { content =>
                       SourceFile(path, content)
                     }
                 }
                 .runCollect
    } yield files
  }

}
