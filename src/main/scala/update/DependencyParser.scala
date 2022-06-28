package update

import zio.{Chunk, ChunkBuilder}
import zio.nio.file.Path

import scala.collection.mutable
import scala.meta._

final case class DependencyWithLocation(
  dependency: Dependency,
  location: Location
)

final case class VersionWithLocation(
  version: Version,
  location: Location
)

final case class Location(path: Path, start: Int, end: Int)

final case class SourceFile(path: Path, string: String) {
  import scala.meta._
  val tree: Tree = dialects.Sbt1(string).parse[Source].get
}

object DependencyParser {

  def getDependencies(sources: Chunk[SourceFile]): Chunk[DependencyWithLocation] = {
    val versionDefs = parseVersionDefs(sources)
    val builder     = ChunkBuilder.make[DependencyWithLocation]()

    sources.foreach { source =>
      source.tree.traverse {
        // Matches String Literal Versions: "zio.dev" %% "zio" % "1.0.0"
        case GroupAndArtifact(group, artifact, term @ Lit.String(version)) =>
          val location   = Location(path = source.path, start = term.pos.start, end = term.pos.end)
          val dependency = Dependency(group, artifact, Version(version))
          builder += DependencyWithLocation(dependency, location)

        // Matches Identifier Versions: "zio.dev" %% "zio" % zioVersion
        //                              "zio.dev" %% "zio" % V.zio
        case GroupAndArtifact(group, artifact, GetIdentifier(name)) if versionDefs.contains(name) =>
          val versionDef = versionDefs(name)
          val location   = versionDef.location
          val dependency = Dependency(group, artifact, versionDef.version)
          builder += DependencyWithLocation(dependency, location)
      }
    }

    builder.result()
  }

  private[update] def parseVersionDefs(sourceFiles: Chunk[SourceFile]): Map[String, VersionWithLocation] =
    sourceFiles.foldLeft(Map.empty[String, VersionWithLocation]) { (acc, sourceFile) =>
      acc ++ parseVersionDefs(sourceFile)
    }

  private[update] def parseVersionDefs(sourceFile: SourceFile): Map[String, VersionWithLocation] = {
    val mutableMap = mutable.Map.empty[String, VersionWithLocation]
    sourceFile.tree.traverse { //
      case q"""val $identifier = ${term @ Lit.String(versionString)}""" =>
        val versionWithLocation =
          VersionWithLocation(Version(versionString), Location(sourceFile.path, term.pos.start, term.pos.end))
        mutableMap += (identifier.syntax -> versionWithLocation)
    }
    mutableMap.toMap
  }

  // # Helper Extractors for parsing dependency information

  private object GroupAndArtifact {
    // Extracts: "group" %% "artifact" % "hello"
    def unapply(tree: Tree): Option[(Group, Artifact, Term)] =
      tree match {
        case Term.ApplyInfix(
              Term.ApplyInfix(Lit.String(group), Term.Name("%" | "%%" | "%%%"), _, List(Lit.String(artifact))),
              Term.Name("%"),
              _,
              List(arg)
            ) =>
          Some((Group(group), Artifact(artifact), arg))
        case _ => None
      }
  }

  private object GetIdentifier {
    def unapply(tree: Tree): Option[String] =
      tree match {
        // V.identifier
        case Term.Select(_, identifier) => Some(identifier.syntax)

        // identifier
        case Term.Name(identifier) => Some(identifier)

        case _ => None
      }
  }
}
