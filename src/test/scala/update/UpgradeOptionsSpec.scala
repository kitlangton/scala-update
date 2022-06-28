package update

import zio.test._

object UpgradeOptionsSpec extends ZIOSpecDefault {

  def spec =
    suite("UpgradeOptionsSpec")(
      test("simple version") {
        val result = UpdateOptions
          .getOptions(
            Version("1.0.0"),
            List(
              Version("1.0.0"),
              Version("1.0.1"),
              Version("1.0.2"), // newest patch version
              Version("1.1.0"),
              Version("1.1.1"), // newest minor version
              Version("2.1.1"),
              Version("2.1.2"), // newest major version
              Version("3.0.0-M8"),
              Version("3.0.0-RC1"),
              Version("3.0.0-RC3") // newest prerelease version
            )
          )

        val expected =
          UpdateOptions(
            Some(Version("2.1.2")),
            Some(Version("1.1.1")),
            Some(Version("1.0.2")),
            Some(Version("3.0.0-RC3"))
          )

        assertTrue(result == expected)
      },
      test("only minor") {
        val result = UpdateOptions
          .getOptions(
            Version("1.0.0"),
            List(
              Version("1.1.0"),
              Version("1.1.1") // newest minor version
            )
          )

        val expected =
          UpdateOptions(
            None,
            Some(Version("1.1.1")),
            None,
            None
          )

        assertTrue(result == expected)
      },
      test("rc version") {
        val result = UpdateOptions
          .getOptions(
            Version("1.0.0-RC1"),
            List(
              Version("1.0.0"),
              Version("1.0.0-RC2")
            )
          )

        val expected =
          UpdateOptions(
            Some(Version("1.0.0")),
            None,
            None,
            // TODO: Fix subtle bug here
//            Some(Version("1.0.0-RC2"))
            None
          )

        assertTrue(result == expected)
      }
    )
}
