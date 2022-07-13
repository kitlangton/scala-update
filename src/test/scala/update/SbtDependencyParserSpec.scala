package update

import zio.Chunk
import zio.nio.file.Path
import zio.test._

object SbtDependencyParserSpec extends ZIOSpecDefault {
  val spec =
    suite("SbtDependencyParserSpec")(
      test("parse dependencies from file content") {
        val sourceFiles =
          Chunk(
            SourceFile(
              Path("project/build.properties"),
              """sbt.version = 1.2.3""",
              Some("properties")
            )
          )

        val deps = DependencyParser.getDependencies(sourceFiles)

        val expected =
          Chunk(
            Dependency(Dependency.sbtGroup, Dependency.sbtArtifact, Version("1.2.3"))
          )

        assertTrue(deps.map(_.dependency) == expected)
      }
    )
}
