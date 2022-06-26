package dependencies

import zio.ZIO
import zio.nio.file.{Files, Path}
import zio.stream.ZStream

import java.io.IOException

object FileUtils {
  def allScalaFiles(root: Path): ZStream[Any, IOException, Path] =
    ZStream.whenZIO(Files.isDirectory(root)) {
      Files
        .newDirectoryStream(root)
        .flatMap { path =>
          for {
            dir <- ZStream.fromZIO(Files.isDirectory(path))
            res <- if (dir) allScalaFiles(path)
                   else ZStream.succeed(path)
          } yield res
        }
        .filter(_.toString().endsWith(".scala"))
    }

}
