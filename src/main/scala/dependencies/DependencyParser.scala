package dependencies

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
        case Term.ApplyInfix(
              Term.ApplyInfix(Lit.String(group), Term.Name("%" | "%%" | "%%%"), _, List(Lit.String(artifact))),
              Term.Name("%"),
              _,
              List(str @ Lit.String(version))
            ) =>
          val location   = Location(path = sourceFile.path, start = str.pos.start, end = str.pos.end)
          val dependency = Dependency(Group(group), Artifact(artifact), Version(version))
          dependencies += DependencyWithLocation(dependency, location)
        case Term.ApplyInfix(
              Term.ApplyInfix(Lit.String(group), Term.Name("%" | "%%" | "%%%"), _, List(Lit.String(artifact))),
              Term.Name("%"),
              _,
              List(ident)
            ) if assignments.contains(getName(ident)) =>
          val assignment = assignments(getName(ident))
          val location   = assignment.location
          val dependency = Dependency(Group(group), Artifact(artifact), assignment.version)
          dependencies += DependencyWithLocation(dependency, location)
        case Term.ApplyInfix(
              Term.ApplyInfix(Lit.String(group), Term.Name("%" | "%%" | "%%%"), _, List(Lit.String(artifact))),
              Term.Name("%"),
              _,
              List(ident)
            ) =>
          throw new Error(s"Could not find version for $group %% $artifact % $ident")
      }
    }
    dependencies.toList
  }

  def getName(tree: Tree): String =
    tree match {
      case Term.Select(_, name) => name.syntax
      case Term.Name(name)      => name
    }
}
