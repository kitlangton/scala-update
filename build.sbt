ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.1"

val zioVersion = "2.0.21"

// /Users/kit/code/terminus
//val terminusLocal = ProjectRef(file("/Users/kit/code/terminus"), "terminusZioJVM")

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "scala-update",
    libraryDependencies ++= Seq(
      "org.scala-lang"       %% "scala3-compiler" % "3.4.1",
      "io.get-coursier"       % "interface"       % "1.0.19",
      "com.lihaoyi"          %% "pprint"          % "0.8.1",
      "dev.zio"              %% "zio-nio"         % "2.0.2",
      "dev.zio"              %% "zio"             % zioVersion,
      "dev.zio"              %% "zio-streams"     % zioVersion,
      "dev.zio"              %% "zio-test"        % zioVersion % Test,
      "io.github.kitlangton" %% "terminus-zio"    % "0.0.9"
    ),
    mainClass          := Some("update.Main"),
    nativeImageVersion := "21.0.2",
    nativeImageJvm     := "graalvm-java21"
  )
//  .dependsOn(terminusLocal)

Global / onChangedBuildSource := ReloadOnSourceChanges
