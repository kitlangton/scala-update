package update.test.utils

import zio.nio.file
import zio.nio.file.Path
import zio.*

import java.io.IOException

object TestFileHelper:
  private def createTempDir: ZIO[Scope, IOException, Path] =
    for tempDir <- file.Files.createTempDirectoryScoped(Some("dependency-updater-spec"), Iterable.empty)
    yield tempDir

  private def createFile(path: Path, content: String): IO[IOException, Unit] =
    ZIO.foreach(path.parent)(file.Files.createDirectories(_)) *>
      ZIO.writeFile(path.toString, content)

  def createTempFiles(files: (String, String)*): ZIO[Scope, IOException, Path] =
    for
      tempDir <- createTempDir
      _ <- ZIO.foreachDiscard(files) { case (path, content) =>
             val filePath = tempDir / path
             createFile(filePath, content)
           }
    yield tempDir
