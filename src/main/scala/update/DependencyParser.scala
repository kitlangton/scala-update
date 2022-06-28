package update

import zio.nio.file.Path

import scala.collection.mutable
import scala.meta._

final case class DependencyWithLocation(
  dependency: Dependency,
  location: Location
)

final case class VersionWithLocation(version: Version, location: Location)

final case class Location(path: Path, start: Int, end: Int)

final case class SourceFile(path: Path, string: String) {
  import scala.meta._
  lazy val tree: Tree =
    dialects.Sbt1(string).parse[Source].get
}

object DependencyParser {

  def getAssignments(sourceFile: SourceFile): Map[String, VersionWithLocation] = {
    val mutableMap = mutable.Map.empty[String, VersionWithLocation]
    sourceFile.tree.traverse { case q"""val $name = ${value @ Lit.String(versionString)}""" =>
      mutableMap += (name.syntax -> VersionWithLocation(
        Version(versionString),
        Location(sourceFile.path, value.pos.start, value.pos.end)
      ))
    }
    mutableMap.toMap
  }

  def getAssignments(sourceFiles: List[SourceFile]): Map[String, VersionWithLocation] =
    sourceFiles.foldLeft(Map.empty[String, VersionWithLocation]) { (acc, sourceFile) =>
      acc ++ getAssignments(sourceFile)
    }

  def getDependencies(sourceFiles: List[SourceFile]): List[DependencyWithLocation] = {
    val assignments  = getAssignments(sourceFiles)
    val dependencies = mutable.ListBuffer.empty[DependencyWithLocation]
    sourceFiles.foreach { sourceFile =>
      sourceFile.tree.traverse {
        // Matches String Literal Versions: "zio.dev" %% "zio" % "1.0.0"
        case GroupAndArtifact(group, artifact, str @ Lit.String(version)) =>
          val location   = Location(path = sourceFile.path, start = str.pos.start, end = str.pos.end)
          val dependency = Dependency(Group(group), Artifact(artifact), Version(version))
          dependencies += DependencyWithLocation(dependency, location)
        // Matches Identifier Versions: "zio.dev" %% "zio" % zioVersion
        //                              "zio.dev" %% "zio" % Dependencies.zio
        case GroupAndArtifact(group, artifact, GetName(name)) if assignments.contains(name) =>
          val assignment = assignments(name)
          val location   = assignment.location
          val dependency = Dependency(Group(group), Artifact(artifact), assignment.version)
          dependencies += DependencyWithLocation(dependency, location)
      }
    }
    dependencies.toList
  }

  private object GroupAndArtifact {
    def unapply(tree: Tree): Option[(String, String, Term)] =
      tree match {
        case Term.ApplyInfix(
              Term.ApplyInfix(Lit.String(group), Term.Name("%" | "%%" | "%%%"), _, List(Lit.String(artifact))),
              Term.Name("%"),
              _,
              List(arg)
            ) =>
          Some((group, artifact, arg))
        case _ => None
      }
  }

  object GetName {
    def unapply(tree: Tree): Option[String] =
      tree match {
        case Term.Select(_, name) => Some(name.syntax)
        case Term.Name(name)      => Some(name)
        case _                    => None
      }
  }
}
