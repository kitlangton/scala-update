package update

import update.model.Dependency
import update.services.Files
import update.services.dependencies.DependencyLoader
import update.test.utils.TestFileHelper
import zio.*
import zio.nio.file
import zio.test.*

object DependencyLoaderSpec extends ZIOSpecDefault:

  val buildSbtString = """
val zioVersion = "1.0.12"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-json" % "0.2.0",
  "io.getquill" %% "quill-jdbc-zio" % Dependencies.quill,
  "dev.cheleb" %% "zio-pravega" % "0.1.0-RC12",
  "org.postgresql" % "postgresql" % "42.2.5"
)
"""

  val dependenciesString = """
object Dependencies {
  val quill = "3.10.0"
}
"""

  def spec =
    suiteAll("DependencyUpdate") {
      test("should update the version of a dependency") {
        for
          dir <- TestFileHelper.createTempFiles(
                   "build.sbt"                  -> buildSbtString,
                   "project/Dependencies.scala" -> dependenciesString
                 )
          dependencies <- DependencyLoader.getDependencies(dir.toString)
        yield assertTrue(
          dependencies.map(_.value).toSet ==
            Set(
              Dependency("dev.zio", "zio", "1.0.12", false),
              Dependency("dev.zio", "zio-json", "0.2.0", false),
              Dependency("io.getquill", "quill-jdbc-zio", "3.10.0", false),
              Dependency("dev.cheleb", "zio-pravega", "0.1.0-RC12", false),
              Dependency("org.postgresql", "postgresql", "42.2.5", true)
            )
        )
      }
    }.provide(
      Scope.default,
      DependencyLoader.live,
      Files.live
    )
