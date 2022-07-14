package update

import zio.Chunk
import zio.nio.file.Path
import zio.test._

object ReplacementSpec extends ZIOSpecDefault {
  def spec =
    suite("ReplacementSpec")(
      test("replace updated versions parsed from build definition with version definitions") {
        val parsed =
          SourceFile(
            Path("fake"),
            """
object V {
  val zio = "1.0.14"
  val `zio-test` = "0.1.0"
  val `zio-spike` = "0.1.0"
  val `zio-json` = "0.1.0"
}
""",
            None
          )

        val assignments = DependencyParser.parseVersionDefs(parsed)

        val result = Replacement.replace(
          parsed.string,
          assignments.values.toList.map(v =>
            Replacement(v.location.start, v.location.end, "\"" + v.version.value + "-RC10" + "\"")
          )
        )

        val expected =
          """
object V {
  val zio = "1.0.14-RC10"
  val `zio-test` = "0.1.0-RC10"
  val `zio-spike` = "0.1.0-RC10"
  val `zio-json` = "0.1.0-RC10"
}
"""

        assertTrue(result == expected)
      },
      test("replace updated versions parsed from build definition with inline versions") {
        val parsed =
          SourceFile(
            Path("build.sbt"),
            """
libraryDependencies ++= Seq(
  "dev.zio" %% "zio"       % "1.0.14",
  "dev.zio" %% "zio-test"  % "0.1.0",
  "dev.zio" %% "zio-spike" % "0.1.0",
  "dev.zio" %% "zio-json"  % "0.1.0"
)
""",
            Some("sbt")
          )

        val assignments = DependencyParser.getDependencies(Chunk.succeed(parsed)).toList

        val result = Replacement.replace(
          parsed.string,
          assignments.toList.map(v =>
            Replacement(v.location.start, v.location.end, "\"" + v.dependency.version.value + "-RC10" + "\"")
          )
        )

        val expected =
          """
libraryDependencies ++= Seq(
  "dev.zio" %% "zio"       % "1.0.14-RC10",
  "dev.zio" %% "zio-test"  % "0.1.0-RC10",
  "dev.zio" %% "zio-spike" % "0.1.0-RC10",
  "dev.zio" %% "zio-json"  % "0.1.0-RC10"
)
"""

        assertTrue(result == expected)
      },
      test("replace updated sbt version parsed from build.properties file") {
        val parsed =
          SourceFile(
            Path("project/build.properties"),
            """sbt.version = 1.2.3""",
            Some("properties")
          )

        val assignments = DependencyParser.getDependencies(Chunk.succeed(parsed)).toList

        val result = Replacement.replace(
          parsed.string,
          assignments.map(v => Replacement(v.location.start, v.location.end, s"${v.dependency.version.value}-RC10"))
        )

        val expected = """sbt.version = 1.2.3-RC10"""

        assertTrue(result == expected)
      }
    )
}
