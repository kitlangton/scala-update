package dependencies

import zio.nio.file.Path
import zio.test._

object DependencyParserSpec extends ZIOSpecDefault {

  val spec =
    suite("DependencyParserSpec")(
      test("parse dependencies from trees") {
        val sourceFiles =
          List(
            SourceFile(
              Path("fake"),
              """

val V = {
  val zio = "1.0.14"
  val `zio-json` = "0.3.0"
  val other = "3.5.0"
}

val X = {
  val zio = "0.0.7"
}

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % V.zio,
  "dev.zio" %%% "zio-test" % V.zioTest % Test,
  "dev.zio" % "zio-json" % V.`zio-json`
)
"""
            ),
            SourceFile(
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
          List(
            Dependency(Group("dev.zio"), Artifact("zio"), Version("1.0.14")),
            Dependency(Group("dev.zio"), Artifact("zio-test"), Version("1.0.14")),
            Dependency(Group("dev.zio"), Artifact("zio-json"), Version("0.3.0")),
            Dependency(Group("other.dev"), Artifact("other"), Version("3.5.0")),
            Dependency(Group("other.dev"), Artifact("other.test"), Version("3.5.8"))
          )

        assertTrue(deps.map(_.dependency) == expected)
      },
      test("parse dependencies from string interpolation") {
        val sourceFiles =
          List(
            SourceFile(
              Path("fake"),
              """
val V = {
  val foo = "1.23.0"
}

libraryDependencies += "org.example" % "artifact" % s"v4-rev516-${V.foo}"
"""
            )
          )

        val obtained = DependencyParser.getDependencies(sourceFiles).head.dependency.version.value
        val expected = "v4-rev516-1.23.0"

        assertTrue(obtained == expected)
      }
    )

}
