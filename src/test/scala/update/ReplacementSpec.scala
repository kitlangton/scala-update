package update

import zio.nio.file.Path
import zio.test._

object ReplacementSpec extends ZIOSpecDefault {
  def spec =
    suite("ReplacementSpec")(
      test("test") {
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
"""
          )

        val assignments = DependencyParser.getAssignments(parsed)

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
      }
    )
}
