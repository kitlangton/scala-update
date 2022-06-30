package update

import update.versions.Versions
import zio.ZIO
import zio.test._

object MillDependencyUpdaterSpec extends ZIOSpecDefault {
  def spec =
    suite("MillDependencyUpdaterSpec")(
      test("updateDependencies") {

        val buildMillString =
          """
import $file.Dependencies, Dependencies.Dependencies

object foo extends ScalaModule {
  def scalaVersion = "2.13.8"
  val zioVersion = "1.0.12"

  def ivyDeps = Agg(
    ivy"dev.zio::zio:$zioVersion",
    ivy"dev.zio::zio-json:0.2.0",
    ivy"io.getquill::quill-jdbc-zio:${Dependencies.quill}",
    ivy"dev.cheleb:zio-pravega:0.1.0-RC12"
  )
}
"""

        val expectedMillString =
          """
import $file.Dependencies, Dependencies.Dependencies

object foo extends ScalaModule {
  def scalaVersion = "2.13.8"
  val zioVersion = "2.0.0"

  def ivyDeps = Agg(
    ivy"dev.zio::zio:$zioVersion",
    ivy"dev.zio::zio-json:0.2.0",
    ivy"io.getquill::quill-jdbc-zio:${Dependencies.quill}",
    ivy"dev.cheleb:zio-pravega:0.1.0-RC12"
  )
}
"""

        val dependenciesString =
          """
object Dependencies {
  val quill = "0.9.0"
}
"""

        val expectedDependenciesString =
          """
object Dependencies {
  val quill = "0.9.5"
}
"""

        ZIO.scoped {
          for {
            tempDir         <- zio.nio.file.Files.createTempDirectoryScoped(Some("dependency-updater-spec"), Iterable.empty)
            buildMillPath    = tempDir / "build.sc"
            dependenciesPath = tempDir / "Dependencies.sc"
            _               <- zio.nio.file.Files.createFile(buildMillPath)
            _               <- zio.nio.file.Files.createDirectory(tempDir / "project")
            _               <- zio.nio.file.Files.createFile(dependenciesPath)
            _               <- ZIO.writeFile(buildMillPath.toString, buildMillString)
            _               <- ZIO.writeFile(dependenciesPath.toString, dependenciesString)
            _               <- TestSystem.putProperty("user.dir", tempDir.toString)
            _               <- ZIO.serviceWithZIO[DependencyUpdater](_.updateDependencies)
            newBuildSbt     <- ZIO.readFile(buildMillPath.toString)
            newDependencies <- ZIO.readFile(dependenciesPath.toString)
          } yield assertTrue(
            newBuildSbt == expectedMillString,
            newDependencies == expectedDependenciesString
          )

        }
      }
    ).provide(
      DependencyUpdater.live,
      Files.live,
      Versions.test(
        Map(
          (Group("dev.zio"), Artifact("zio"))                -> List(Version("2.0.0")),
          (Group("io.getquill"), Artifact("quill-jdbc-zio")) -> List(Version("0.9.5")),
          (Group("dev.zio"), Artifact("zio-json"))           -> List(Version("0.3.1")),
          (Group("dev.cheleb"), Artifact("zio-pravega"))     -> List(Version("0.1.0-RC9"))
        )
      )
    )
}
