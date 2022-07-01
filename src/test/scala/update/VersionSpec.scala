package update

import zio.test._

object VersionSpec extends ZIOSpecDefault {
  def spec = suite("Version")(
    test("ordering") {
      val versions = List(
        Version("1.10.0"),
        Version("1.10.1"),
        Version("1.5.0"),
        Version("1.5.1"),
        Version("1.6.0"),
        Version("1.7.0"),
        Version("1.7.1"),
        Version("1.8.0"),
        Version("2.0.0"),
        Version("1.9.0")
      )

      val expected =
        List(
          Version("1.5.0"),
          Version("1.5.1"),
          Version("1.6.0"),
          Version("1.7.0"),
          Version("1.7.1"),
          Version("1.8.0"),
          Version("1.9.0"),
          Version("1.10.0"),
          Version("1.10.1"),
          Version("2.0.0")
        )

      val result: List[Version] =
        versions.sorted

      assertTrue(result == expected)
    }
  )
}
