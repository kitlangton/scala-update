package update.services

import update.model.*
import update.services.dependencies.DependencyLoader
import update.test.utils.TestFileHelper
import zio.*
import zio.test.*

object ScalaUpdateSpec extends ZIOSpecDefault:

  //////////////////////////
  // Original Build Files //
  //////////////////////////

  val buildSbtString = """
val zioVersion = "1.0.12"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-json" % "0.2.0",
  "io.getquill" %% "quill-jdbc-zio" % Dependencies.quill,
  "dev.cheleb" %% "zio-pravega" % "0.1.0-RC12",
  "org.postgresql" % "postgresql" % Dependencies.postgresVersion
)
"""

  val expectedBuildSbtString =
    """
val zioVersion = "2.0.0"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-json" % "2.2.2",
  "io.getquill" %% "quill-jdbc-zio" % Dependencies.quill,
  "dev.cheleb" %% "zio-pravega" % "0.1.0-RC12",
  "org.postgresql" % "postgresql" % Dependencies.postgresVersion
)
"""

  val buildMillString = """
import $file.Dependencies, Dependencies.Dependencies

object foo extends ScalaModule {
  def scalaVersion = "2.13.8"
  val zioJsonVersion = "1.2.2"

  def ivyDeps = Agg(
    ivy"dev.zio::zio-json:$zioJsonVersion",
    ivy"io.getquill::quill-jdbc-zio:${Dependencies.quill}",
    ivy"dev.cheleb:zio-pravega:0.1.0-RC12",
    ivy"org.postgresql:postgresql:1.2.3",
  )
}
"""

  val expectedMillString = """
import $file.Dependencies, Dependencies.Dependencies

object foo extends ScalaModule {
  def scalaVersion = "2.13.8"
  val zioJsonVersion = "2.2.2"

  def ivyDeps = Agg(
    ivy"dev.zio::zio-json:$zioJsonVersion",
    ivy"io.getquill::quill-jdbc-zio:${Dependencies.quill}",
    ivy"dev.cheleb:zio-pravega:0.1.0-RC12",
    ivy"org.postgresql:postgresql:42.2.6",
  )
}
"""

  val dependenciesString =
    """
object Dependencies {
  val quill = "3.10.0"
  def postgresVersion = "42.2.0"
}
"""

  val expectedDependenciesString =
    """
object Dependencies {
  val quill = "3.11.0"
  def postgresVersion = "42.2.6"
}
"""

  val buildPropertiesString = """
sbt.version=1.5.5
"""

  val pluginsSbtString = """
addSbtPlugin("org.scalafmt" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("org.scalameta" % "sbt-scalafix" % "0.9.31")
"""

  /////////////////////
  // Latest Versions //
  /////////////////////

  val stubVersions = Map(
    (Group("dev.zio"), Artifact("zio"))                -> List(Version("1.0.13"), Version("2.0.0")),
    (Group("dev.zio"), Artifact("zio-json"))           -> List(Version("2.2.2")),
    (Group("io.getquill"), Artifact("quill-jdbc-zio")) -> List(Version("3.11.0")),
    (Group("dev.cheleb"), Artifact("zio-pravega"))     -> List(Version("0.1.0-RC13")),
    (Group("org.postgresql"), Artifact("postgresql"))  -> List(Version("42.2.6")),
    // plugins
    (Group("org.scalafmt"), Artifact("sbt-scalafmt"))  -> List(Version("2.5.0")),
    (Group("org.scalameta"), Artifact("sbt-scalafix")) -> List(Version("0.9.32")),
    // build properties
    (Group("org.scala-sbt"), Artifact("sbt")) -> List(Version("1.9.9"))
  )

  //////////////////////
  // Expected Results //
  //////////////////////

  val expectedBuildPropertiesString = """
sbt.version=1.9.9
"""

  val expectedPluginsSbtString = """
addSbtPlugin("org.scalafmt" % "sbt-scalafmt" % "2.5.0")
addSbtPlugin("org.scalameta" % "sbt-scalafix" % "0.9.32")
"""

  def spec =
    suiteAll("ScalaUpdate") {
      test("updates to the latest version of the dependencies") {
        for
          root <- TestFileHelper.createTempFiles(
                    "build.sbt"                  -> buildSbtString,
                    "project/Dependencies.scala" -> dependenciesString,
                    "project/plugins.sbt"        -> pluginsSbtString,
                    "project/build.properties"   -> buildPropertiesString,
                    "build.sc"                   -> buildMillString
                  )
          _ <- ScalaUpdate.updateAllDependencies(root.toString)

          newBuildSbt        <- ZIO.readFile((root / "build.sbt").toString)
          newDependencies    <- ZIO.readFile((root / "project" / "Dependencies.scala").toString)
          newPluginsSbt      <- ZIO.readFile((root / "project" / "plugins.sbt").toString)
          newBuildProperties <- ZIO.readFile((root / "project" / "build.properties").toString)
          newBuildMill       <- ZIO.readFile((root / "build.sc").toString)
        yield assertTrue(
          newBuildSbt == expectedBuildSbtString,
          newDependencies == expectedDependenciesString,
          newPluginsSbt == expectedPluginsSbtString,
          newBuildProperties == expectedBuildPropertiesString,
          newBuildMill == expectedMillString
        )
      }
    }.provide(
      Scope.default,
      DependencyLoader.live,
      Files.live,
      ScalaUpdate.layer,
      VersionsInMemory.layer(stubVersions)
    )
