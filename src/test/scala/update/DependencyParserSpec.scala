package update

import zio.Chunk
import zio.nio.file.Path
import zio.test._

object DependencyParserSpec extends ZIOSpecDefault {

  val spec =
    suite("DependencyParserSpec")(
      test("parse dependencies from trees") {
        val sourceFiles =
          Chunk(
            SourceFile.Sbt1DialectSourceFile(
              Path("fake"),
              """
val V = {
  val zio = "1.0.14"
  val `zio-json` = "0.3.0"
  val other = "3.5.0"
}

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % V.zio,
  "dev.zio" %%% "zio-test" % V.zioTest % Test,
  "dev.zio" % "zio-json" % V.`zio-json`
)
"""
            ),
            SourceFile.Sbt1DialectSourceFile(
              Path("fake"),
              """
val V = {
  val zioTest = "1.0.14"
}

libraryDependencies ++= Seq(
  "other.dev" % "other" % V.other,
  "other.dev" %%% "other.test" % "3.5.8" % Test
)
"""
            )
          )

        val deps = DependencyParser.getDependencies(sourceFiles)

        val expected =
          Chunk(
            Dependency(Group("dev.zio"), Artifact("zio"), Version("1.0.14")),
            Dependency(Group("dev.zio"), Artifact("zio-test"), Version("1.0.14")),
            Dependency(Group("dev.zio"), Artifact("zio-json"), Version("0.3.0")),
            Dependency(Group("other.dev"), Artifact("other"), Version("3.5.0")),
            Dependency(Group("other.dev"), Artifact("other.test"), Version("3.5.8"))
          )

        assertTrue(deps.map(_.dependency) == expected)
      }
    )

}
