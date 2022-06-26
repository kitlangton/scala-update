package dependencies

import zio.nio.file.Path
import zio.stream.ZStream
import zio.{Chunk, IO, ZIO}

import java.io.IOException

final case class FilesLive() extends Files {
  override def allScalaFiles(path: String): IO[IOException, Chunk[SourceFile]] = {
    val path0 = Path(path)
    FileUtils
      .allScalaFiles(path0 / "project")
      .concat(ZStream.succeed(path0 / "build.sbt"))
      .mapZIO { path =>
        ZIO
          .readFile(path.toString)
          .map { content =>
            SourceFile(path, content)
          }
      }
      .runCollect
  }
}
