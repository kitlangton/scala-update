inThisBuild(
  List(
    name               := "scala-interactive-update",
    normalizedName     := "scala-interactive-update",
    organization       := "io.github.kitlangton",
    scalaVersion       := "2.13.8",
    crossScalaVersions := Seq("2.13.8"),
    homepage           := Some(url("https://github.com/kitlangton/scala-interactive-update")),
    licenses           := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "kitlangton",
        "Kit Langton",
        "kit.langton@gmail.com",
        url("https://github.com/kitlangton")
      )
    )
  )
)

val zioVersion      = "2.0.0"
val zioNioVersion   = "2.0.0"
val coursierVersion = "2.1.0-M6-28-gbad85693f"

lazy val root = (project in file("."))
  .settings(
    name := "scala-interactive-update",
    libraryDependencies ++= Seq(
      "dev.zio"              %% "zio"          % zioVersion,
      "dev.zio"              %% "zio-macros"   % zioVersion,
      "dev.zio"              %% "zio-nio"      % zioNioVersion,
      "dev.zio"              %% "zio-streams"  % zioVersion,
      "dev.zio"              %% "zio-test"     % zioVersion % Test,
      "dev.zio"              %% "zio-test-sbt" % zioVersion % Test,
      "io.get-coursier"      %% "coursier"     % coursierVersion,
      "org.scalameta"        %% "scalameta"    % "4.5.9",
      "io.github.kitlangton" %% "zio-tui"      % "0.1.2"
    ),
    Compile / mainClass := Some("update.Main"),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    graalVMNativeImageOptions ++= Seq(
      "--no-fallback",
      "--enable-url-protocols=https"
    )
  )
  .enablePlugins(GraalVMNativeImagePlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges
