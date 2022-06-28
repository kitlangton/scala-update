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

  case class Scope(parent: Option[String], name: String)

  def getAssignments(sourceFile: SourceFile): Map[Scope, VersionWithLocation] = {
    val mutableMap = mutable.Map.empty[Scope, VersionWithLocation]

    def assign(parent: Option[String], stats: List[Tree]): Unit =
      stats.foreach {
        case tree @ q"""val $name = ${value @ Lit.String(versionString)}""" =>
          mutableMap += (Scope(parent, name.syntax) -> VersionWithLocation(
            Version(versionString),
            Location(sourceFile.path, value.pos.start, value.pos.end)
          ))          
      }    


    sourceFile.tree.traverse {
      case q"""val $name = { ..$stats}""" =>
        assign(Some(name.syntax), stats)

      case Defn.Object(_, Term.Name(name), template) =>
        assign(Some(name), template.stats)
    }

    mutableMap.toMap
  }

  private def getAssignments(sourceFiles: List[SourceFile]): Map[Scope, VersionWithLocation] =
    sourceFiles.foldLeft(Map.empty[Scope, VersionWithLocation]) { (acc, sourceFile) =>
      acc ++ getAssignments(sourceFile)
    }

  private object GroupAndArtifact {
    def unapply(tree: Tree): Option[(String, String, List[Term])] = {
      tree match {
        case Term.ApplyInfix(
            Term.ApplyInfix(Lit.String(group), Term.Name("%" | "%%" | "%%%"), _, List(Lit.String(artifact))),
            Term.Name("%"),
            _,
            args
          ) => Some((group, artifact, args))
        case _ => None
      }
    }
  }

  def getDependencies(sourceFiles: List[SourceFile]): List[DependencyWithLocation] = {
    val assignments  = getAssignments(sourceFiles)

    val dependencies = mutable.ListBuffer.empty[DependencyWithLocation]
    sourceFiles.foreach { sourceFile =>
      sourceFile.tree.traverse {
        case GroupAndArtifact(group, artifact, List(str @ Lit.String(version))) =>
          val location   = Location(path = sourceFile.path, start = str.pos.start, end = str.pos.end)
          val dependency = Dependency(Group(group), Artifact(artifact), Version(version))
          dependencies += DependencyWithLocation(dependency, location)
        case GroupAndArtifact(group, artifact, List(ident)) if assignments.contains(getScope(ident)) =>
          val assignment = assignments(getScope(ident))

          val version = 
            ident match {
              case Term.Interpolate(Term.Name("s"), parts, _) =>

                def interleave(left: List[String], right: List[String]): String =
                  left.zipAll(right, "", "").map {case (l, r) => l + r}.mkString("")

                Version(interleave(parts.map(_.syntax), List(assignment.version.value)))
              case _ => assignment.version
            }

          val location   = assignment.location
          val dependency = Dependency(Group(group), Artifact(artifact), version)
          dependencies += DependencyWithLocation(dependency, location)
        case GroupAndArtifact(group, artifact, List(ident)) =>
         // throw new Error(s"Could not find version for $group %% $artifact % $ident")
      }
    }
    dependencies.toList
  }

  private def getScope(tree: Tree): Scope = {
    tree match {
      case Term.Select(parent, name) =>
        Scope(Some(parent.syntax), name.syntax)
      case Term.Name(name) =>
        Scope(None, name)
      case Term.Interpolate(Term.Name("s"), _, List(Term.Select(parent, name))) =>
        Scope(Some(parent.syntax), name.syntax)
    }
  }
}
