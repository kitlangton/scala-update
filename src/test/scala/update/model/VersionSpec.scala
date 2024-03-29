package update.model

import zio.test.*

object VersionSpec extends ZIOSpecDefault:
  private val parsingExpectations =
    List(
      "1.0.0"         -> Version.SemVer(1, 0, 0, None),
      "1.0.0-RC4"     -> Version.SemVer(1, 0, 0, Some(PreRelease.RC(4))),
      "2.0-RC4"       -> Version.SemVer(2, 0, 0, Some(PreRelease.RC(4))),
      "1.5.5-M1"      -> Version.SemVer(1, 5, 5, Some(PreRelease.M(1))),
      "4.5.5.5"       -> Version.Other("4.5.5.5"),
      "i-hate-semver" -> Version.Other("i-hate-semver"),
      "1.1.1-alpha"   -> Version.SemVer(1, 1, 1, Some(PreRelease.Alpha(None))),
      "1.1.1-alpha.5" -> Version.SemVer(1, 1, 1, Some(PreRelease.Alpha(Some(5)))),
      "1.1.1-beta"    -> Version.SemVer(1, 1, 1, Some(PreRelease.Beta(None))),
      "1.1.1-beta.5"  -> Version.SemVer(1, 1, 1, Some(PreRelease.Beta(Some(5))))
    )

  private val orderingExpectations =
    List(
      List("1.1.1", "2.0.0", "0.0.5") -> List("0.0.5", "1.1.1", "2.0.0"),
      List(
        "0.5.0",
        "0.1.0-RC12",
        "0.1.0-RC13",
        "1.0.0",
        "1.0.0-RC2",
        "1.0.0-RC1",
        "1.0.0-M1",
        "1.0.0-alpha",
        "1.0.0-alpha.1",
        "2.0.0",
        "2.1.0",
        "2.0.1"
      ) -> List(
        "0.1.0-RC12",
        "0.1.0-RC13",
        "0.5.0",
        "1.0.0-alpha",
        "1.0.0-alpha.1",
        "1.0.0-M1",
        "1.0.0-RC1",
        "1.0.0-RC2",
        "1.0.0",
        "2.0.0",
        "2.0.1",
        "2.1.0"
      )
    ).map { (input, expected) =>
      input.map(Version(_)) -> expected.map(Version(_))
    }

  def spec =
    suiteAll("Version") {

      suite("Parsing")(
        parsingExpectations.map { (input, expected) =>
          test(s"parse $input") {
            assertTrue(Version(input) == expected)
          }
        }*
      )

      suite("Ordering")(
        orderingExpectations.map { (input, expected) =>
          test(s"order $input") {
            assertTrue(input.sorted == expected)
          }
        }*
      )

    }
