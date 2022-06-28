inThisBuild(
  List(
    name               := "scala-interactive-update",
    normalizedName     := "scala-interactive-update",
    organization       := "io.github.kitlangton",
    scalaVersion       := "2.13.8",
    crossScalaVersions := Seq("2.13.8"),
    homepage           := Some(url("https://github.com/kitlangton/scala-interactive-update")),
    licenses           := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    semanticdbEnabled  := true,
    semanticdbVersion  := scalafixSemanticdb.revision,
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
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-unchecked",
      "-Wunused"
//      "-Xfatal-warnings"
    )
  )
  .enablePlugins(GraalVMNativeImagePlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges

// I use this so I can run `sbt run` in this project. Very lazy hack.
//lazy val example = Seq(
//  "dev.zio"                       %% "zio"                           % "1.0.14",
//  "dev.zio"                       %% "zio-macros"                    % "1.0.14",
//  "dev.zio"                       %% "zio-nio"                       % zioNioVersion,
//  "dev.zio"                       %% "zio-streams"                   % "1.0.14",
//  "dev.zio"                       %% "zio-test"                      % "1.0.14" % Test,
//  "dev.zio"                       %% "zio-test-sbt"                  % "1.0.14" % Test,
//  "io.get-coursier"               %% "coursier"                      % coursierVersion,
//  "org.scalameta"                 %% "scalameta"                     % "4.5.8",
//  "io.github.kitlangton"          %% "zio-tui"                       % "0.1.1",
//  "io.github.neurodyne"           %% "zio-aws-s3"                    % "0.4.12",
//  "io.d11"                        %% "zhttp"                         % "2.0.0-RC8",
//  "com.coralogix"                 %% "zio-k8s-client"                % "1.4.6",
//  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.6.1",
//  "nl.vroste"                     %% "zio-kinesis"                   % "0.21.1",
//  "com.vladkopanev"               %% "zio-saga-core"                 % "0.3.0",
//  "io.scalac"                     %% "zio-slick-interop"             % "0.3",
//  "com.typesafe.slick"            %% "slick-hikaricp"                % "3.3.2",
//  "info.senia"                    %% "zio-test-akka-http"            % "1.0.14",
//  "io.getquill"                   %% "quill-jdbc-zio"                % "3.18.0",
//  "dev.zio"                       %% "zio-akka-cluster"              % "0.3.0",
//  "dev.zio"                       %% "zio-cache"                     % "0.2.0",
//  "dev.zio"                       %% "zio-config-magnolia"           % "3.0.1",
//  "dev.zio"                       %% "zio-config-typesafe"           % "3.0.1",
//  "dev.zio"                       %% "zio-config-refined"            % "3.0.1",
//  "dev.zio"                       %% "zio-ftp"                       % "0.3.6",
//  "dev.zio"                       %% "zio-json"                      % "0.3.0-RC8",
//  //      "dev.zio"               %% "zio-kafka"                   % "2.0.0-RC5",
//  "dev.zio"            %% "zio-logging"                 % "0.5.14",
//  "dev.zio"            %% "zio-metrics-prometheus"      % "1.0.14",
//  "dev.zio"            %% "zio-nio"                     % "1.0.0-RC11",
//  "dev.zio"            %% "zio-optics"                  % "0.2.0",
//  "dev.zio"            %% "zio-prelude"                 % "1.0.0-RC9",
//  "dev.zio"            %% "zio-process"                 % "0.7.0",
//  "dev.zio"            %% "zio-rocksdb"                 % "0.3.2",
//  "dev.zio"            %% "zio-s3"                      % "0.3.7",
//  "dev.zio"            %% "zio-schema"                  % "0.2.0",
//  "dev.zio"            %% "zio-sqs"                     % "0.4.3",
//  "dev.zio"            %% "zio-opentracing"             % "0.8.3",
//  "io.laserdisc"       %% "tamer-db"                    % "0.18.1",
//  "io.jaegertracing"    % "jaeger-core"                 % "1.6.0",
//  "io.jaegertracing"    % "jaeger-client"               % "1.6.0",
//  "io.jaegertracing"    % "jaeger-zipkin"               % "1.6.0",
//  "io.zipkin.reporter2" % "zipkin-reporter"             % "2.16.3",
//  "io.zipkin.reporter2" % "zipkin-sender-okhttp3"       % "2.16.3",
//  "dev.zio"            %% "zio-interop-cats"            % "3.3.0",
//  "dev.zio"            %% "zio-interop-scalaz7x"        % "7.3.3.0",
//  "dev.zio"            %% "zio-interop-reactivestreams" % "1.3.12",
//  "dev.zio"            %% "zio-interop-twitter"         % "20.10.2",
//  "dev.zio"            %% "zio-zmx"                     % "0.0.13",
//  "dev.zio"            %% "zio-query"                   % "0.3.0",
//  "org.polynote"       %% "uzhttp"                      % "0.2.8"
//)
