package update

import zio.Chunk
import zio.nio.file.Path
import zio.test._

object MillDependencyParserSpec extends ZIOSpecDefault {

  val spec =
    suite("MillDependencyParserSpec")(
      test("parse dependencies from trees") {
        val sourceFiles =
          Chunk(
            SourceFile(
              Path("fake"),
              """
import mill._, scalalib._

object V {
  val zio = "1.0.14"
  val `zio-json` = "0.3.0"
  val zioTest = "1.0.14"
}

object foo extends ScalaModule {
  def scalaVersion = "2.13.8"
  import V._
  def ivyDeps = Agg(
    ivy"dev.zio::zio:$zio",
    ivy"dev.zio::zio-test::${V.zioTest}",
    ivy"dev.zio::zio-json:${V.`zio-json`}",
  )
}

""",
              None
            )
          )

        val deps = DependencyParser.getDependencies(sourceFiles)

        val expected =
          Chunk(
            Dependency(Group("dev.zio"), Artifact("zio"), Version("1.0.14")),
            Dependency(Group("dev.zio"), Artifact("zio-test"), Version("1.0.14")),
            Dependency(Group("dev.zio"), Artifact("zio-json"), Version("0.3.0"))
          )

        assertTrue(deps.map(_.dependency) == expected)
      },
      test("parse dependencies from multiple files") {
        val sourceFiles =
          Chunk(
            SourceFile(
              Path("fake"),
              """
import mill._, scalalib._
import $file.Versions, Versions.V, Versions.V._

object foo extends ScalaModule {
  def scalaVersion = "2.13.8"
  def ivyDeps = Agg(
    ivy"dev.zio::zio:$zio",
    ivy"dev.zio::zio-test::${V.zioTest}",
    ivy"dev.zio::zio-json:${V.`zio-json`}",
  )
}

""",
              None
            ),
            SourceFile(
              Path("Versions.sc"),
              """
import mill._, scalalib._

object V {
  val zio = "1.0.14"
  val `zio-json` = "0.3.0"
  val zioTest = "1.0.14"
}
""",
              Some("sc")
            )
          )

        val deps = DependencyParser.getDependencies(sourceFiles)

        val expected =
          Chunk(
            Dependency(Group("dev.zio"), Artifact("zio"), Version("1.0.14")),
            Dependency(Group("dev.zio"), Artifact("zio-test"), Version("1.0.14")),
            Dependency(Group("dev.zio"), Artifact("zio-json"), Version("0.3.0"))
          )

        assertTrue(deps.map(_.dependency) == expected)
      }
    )

}
