package dependencies

import zio.{Chunk, IO, ULayer, ZLayer}

import java.io.IOException

trait Files {
  def allScalaFiles(path: String): IO[IOException, Chunk[SourceFile]]
}

object Files {
  val live: ULayer[FilesLive] =
    ZLayer.succeed(FilesLive())
}
