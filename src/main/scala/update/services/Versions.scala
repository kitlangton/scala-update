package update.services

import coursierapi.Complete
import update.model.*
import zio.*

import scala.jdk.CollectionConverters.*

trait Versions:
  def getVersions(group: Group, artifact: Artifact, isJava: Boolean): Task[List[Version]]

  def getVersions(dependency: Dependency): Task[List[Version]] =
    getVersions(dependency.group, dependency.artifact, dependency.isJava)

object Versions:
  val live = ZLayer.fromFunction(() => VersionsLive())

final case class VersionsLive() extends Versions:
  val cache = coursierapi.Cache.create()

  def getVersions(group: Group, artifact: Artifact, isJava: Boolean): Task[List[Version]] =
    val coursierVersions = coursierapi.Versions.create().withCache(cache)
    val coursierComplete = Complete.create()
    ZIO.attemptBlocking {

      // If %% is used, we need to expand the artifact to include the scala version
      // so we can use Coursier's completion API to get the full list of artifacts
      // where the scala version is included in the artifact name, e.g. cats-core_2.13.
      // Otherwise, for "isJava" artifacts, we can just use the artifact name as is
      val expandedArtifacts =
        Option(
          coursierComplete
            .withInput(s"${group.value}:${artifact.value}_")
            .complete()
            .getCompletions
            .asScala
        ).filter(_.nonEmpty)
          .getOrElse(List(artifact.value))

      val versions =
        expandedArtifacts.toList.flatMap { artifact =>
          coursierVersions
            .withModule(coursierapi.Module.of(group.value, artifact))
            .versions()
            .getMergedListings
            .getAvailable
            .asScala
            .map(v => Version(v))
            .toList
        }.distinct

      versions
    }

final case class VersionsInMemory(versions: Map[(Group, Artifact), List[Version]]) extends Versions:
  override def getVersions(group: Group, artifact: Artifact, isJava: Boolean): Task[List[Version]] =
    ZIO.succeed(versions.getOrElse((group, artifact), Nil))

object VersionsInMemory:
  def layer(versions: Map[(Group, Artifact), List[Version]]): ULayer[Versions] =
    ZLayer.succeed(VersionsInMemory(versions))

//object TestVersions extends ZIOAppDefault:
//
//  val tests = List(
////    Dependency(Group("dev.zio"), Artifact("zio"), Version("1.0.0")),
////    Dependency(Group("org.typelevel"), Artifact("cats-core"), Version("2.0.0")),
////    Dependency(Group("com.github.sbt"), Artifact("sbt-native-packager"), Version("1.9.11")),
////    Dependency(Group("com.github.sbt"), Artifact("sbt-ci-release"), Version("1.5.11")),
////    Dependency(Group("ch.epfl.scala"), Artifact("sbt-scalafix"), Version("0.10.4")),
////    Dependency(Group("org.scalameta"), Artifact("sbt-scalafmt"), Version("0.10.4")),
////    Dependency(Group("org.scala-sbt"), Artifact("sbt"), Version("1.5.5")),
//    // "org.scala-lang" %% "scala3-compiler" % "0.5.4"
////    Dependency(Group("org.scala-lang"), Artifact("scala3-compiler"), Version("0.5.4")),
//    // io.get-coursier
////    Dependency(Group("io.get-coursier"), Artifact("interface"), Version("2.0.0-RC6"))
//// com.github.sbt % sbt-native-packager
//    Dependency(Group("com.github.sbt"), Artifact("sbt-native-packager"), Version("1.9.11"))
//    // postgres
////    Dependency(Group("org.postgresql"), Artifact("postgresql"), Version("42.2.23"))
//  )
//
//  def run =
//    ZIO.foreach(tests) { dependency =>
//      VersionsLive()
//        .getVersions(dependency)
//        .map(_.mkString(", "))
//        .debug(s"${dependency.group}:${dependency.artifact}")
//    }
