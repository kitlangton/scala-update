package update.utils

import zio.nio.file.{Files, Path}
import zio.stream.ZStream

import java.io.IOException

object FileUtils:

  /** Returns a Stream of all Scala
    */
  def allScalaFiles(path: Path): ZStream[Any, IOException, Path] =
    allFilesWithExt(path, ".scala")

  /** Returns a Stream of all mill build file (ammonite script)
    */
  def allMillFiles(path: Path): ZStream[Any, IOException, Path] =
    allFilesWithExt(path, ".sc")

  /** Returns file extension of given path, if it exists
    */
  def extension(path: Path): Option[String] =
    path.filename.toString().split('.').lastOption

  private def allFilesWithExt(path: Path, extension: String): ZStream[Any, IOException, Path] =
    ZStream.whenZIO(Files.isDirectory(path)) {
      Files
        .newDirectoryStream(path)
        .flatMap { path =>
          for
            isDir <- ZStream.fromZIO(Files.isDirectory(path))
            res <- if isDir then allScalaFiles(path)
                   else ZStream.succeed(path)
          yield res
        }
        .filter(_.toString.endsWith(extension))
    }
