package update

import zio.test._

object UpdateOptionsSpec extends ZIOSpecDefault {
  def spec =
    suite("UpdateOptionsSpec")(
      test("correctly categorizes updates") {
        val result = UpdateOptions.getOptions(
          Version("1.0.0"),
          List(
            Version("1.0.0"),
            Version("1.0.1"),
            Version("1.0.2"),
            Version("1.1.2"),
            Version("1.2.0"),
            Version("1.2.1"),
            Version("2.2.2"),
            Version("2.2.3")
          )
        )

        val expected =
          UpdateOptions(
            major = Some(Version("2.2.3")),
            minor = Some(Version("1.2.1")),
            patch = Some(Version("1.0.2")),
            preRelease = None
          )

        assertTrue(result == expected)
      },
      test("correctly sorts dependencies") {
        val result = UpdateOptions.getOptions(
          Version("1.9.0"),
          List(
            Version("1.10.0"),
            Version("1.5.1"),
            Version("1.7.0"),
            Version("1.10.1"),
            Version("1.5.0"),
            Version("1.6.0"),
            Version("1.7.1"),
            Version("1.8.0"),
            Version("1.9.0")
          )
        )

        val expected =
          UpdateOptions(
            major = None,
            minor = Some(Version("1.10.1")),
            patch = None,
            preRelease = None
          )

        assertTrue(result == expected)
      },
      test("sorts pre-releases") {

        val result = UpdateOptions.getOptions(
          Version("1.0.0"),
          List(
            Version("1.0.1"),
            Version("2.0.0-RC2"),
            Version("2.0.0-M3"),
            Version("2.0.0-RC1")
          )
        )

        val expected =
          UpdateOptions(
            major = None,
            minor = None,
            patch = Some(Version("1.0.1")),
            preRelease = Some(Version("2.0.0-RC2"))
          )

        assertTrue(result == expected)

      }
    )
}
