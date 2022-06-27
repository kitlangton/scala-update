ThisBuild / scalaVersion     := "2.13.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

val zioVersion      = "2.0.0"
val zioNioVersion   = "2.0.0"
val coursierVersion = "2.1.0-M6-28-gbad85693f"

lazy val root = (project in file("."))
  .settings(
    name := "sbt-interactive-update",
    libraryDependencies ++= Seq(
      "dev.zio"              %% "zio"          % zioVersion,
      "dev.zio"              %% "zio-macros"   % zioVersion,
      "dev.zio"              %% "zio-nio"      % zioNioVersion,
      "dev.zio"              %% "zio-streams"  % zioVersion,
      "dev.zio"              %% "zio-test"     % zioVersion % Test,
      "dev.zio"              %% "zio-test-sbt" % zioVersion % Test,
      "io.get-coursier"      %% "coursier"     % coursierVersion,
      "org.scalameta"        %% "scalameta"    % "4.5.9",
      "io.github.kitlangton" %% "zio-tui"      % "0.0.1"
    ),
//    Compile / mainClass := Some("dependencies.cli.Main"),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    graalVMNativeImageOptions ++= Seq(
      "--no-fallback"
    )
  )
  .enablePlugins(GraalVMNativeImagePlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges
