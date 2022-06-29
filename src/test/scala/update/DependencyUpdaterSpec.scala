package update

import update.versions.Versions
import zio.ZIO
import zio.test._

object DependencyUpdaterSpec extends ZIOSpecDefault {
  def spec =
    suite("DependencyUpdaterSpec")(
      test("updateDependencies") {

        val buildSbtString =
          """
val zioVersion = "1.0.12"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-json" % "0.2.0",
  "io.getquill" %% "quill-jdbc-zio" % Dependencies.quill,
  "dev.cheleb" %% "zio-pravega" % "0.1.0-RC12"
)
"""

        val expectedSbtString =
          """
val zioVersion = "2.0.0"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-json" % "0.3.1",
  "io.getquill" %% "quill-jdbc-zio" % Dependencies.quill,
  "dev.cheleb" %% "zio-pravega" % "0.1.0-RC12"
)
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
            buildSbtPath     = tempDir / "build.sbt"
            dependenciesPath = tempDir / "project" / "Dependencies.scala"
            _               <- zio.nio.file.Files.createFile(buildSbtPath)
            _               <- zio.nio.file.Files.createDirectory(tempDir / "project")
            _               <- zio.nio.file.Files.createFile(dependenciesPath)
            _               <- ZIO.writeFile(buildSbtPath.toString, buildSbtString)
            _               <- ZIO.writeFile(dependenciesPath.toString, dependenciesString)
            _               <- TestSystem.putProperty("user.dir", tempDir.toString)
            _               <- ZIO.serviceWithZIO[DependencyUpdater](_.updateDependencies)
            newBuildSbt     <- ZIO.readFile(buildSbtPath.toString)
            newDependencies <- ZIO.readFile(dependenciesPath.toString)
          } yield assertTrue(
            newBuildSbt == expectedSbtString,
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
