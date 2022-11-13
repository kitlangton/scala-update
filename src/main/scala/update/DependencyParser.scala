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
  location: Location,
  quote: Boolean = true
)

final case class Location(path: Path, start: Int, end: Int)

final case class SourceFile(path: Path, content: String, extension: Option[String]) {
  import scala.meta._
  lazy val tree: Tree = dialects.Sbt1(content).parse[Source].get

  val isPropertiesFile: Boolean = extension.contains("properties")
}

object DependencyParser {
  def getDependencies(sources: Chunk[SourceFile]): Chunk[DependencyWithLocation] = {
    val versionDefs = parseVersionDefs(sources)
    val builder     = ChunkBuilder.make[DependencyWithLocation]()

    sources.foreach {
      case source @ SbtVersionFile(version, start) =>
        val dependency = Dependency.sbt(version.value)
        val location   = Location(source.path, start, source.content.length)
        builder += DependencyWithLocation(dependency, location)

      case source =>
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
          // Matches String Literal Versions: "zio.dev::zio:1.0.0"
          case MillGroupArtifact(group, artifact, term @ Lit.String(version)) =>
            val location   = Location(path = source.path, start = term.pos.start, end = term.pos.end)
            val dependency = Dependency(group, artifact, Version(version))
            builder += DependencyWithLocation(dependency, location)
          // Matches Identifier Versions: "zio.dev::zio:$zioVersion"
          //                              "zio.dev::zio:${V.zio}"
          case MillGroupArtifact(group, artifact, GetIdentifier(name)) if versionDefs.contains(name) =>
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
    sourceFile match {
      case s if s.isPropertiesFile =>
      // No need to parse properties file for version definition values as this is most probably
      // sbt version file and it only contains sbt version definition in a specific format.

      case _ =>
        sourceFile.tree.traverse { //
          case q"""val $identifier = ${term @ Lit.String(versionString)}""" =>
            val versionWithLocation =
              VersionWithLocation(Version(versionString), Location(sourceFile.path, term.pos.start, term.pos.end))
            mutableMap += (identifier.syntax -> versionWithLocation)
        }
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

  private object MillGroupArtifact {
    private val artifact               = raw"([^:]*)::([^:]*)::?(\S)".r
    private val artifactWithoutVersion = raw"([^:]*)::([^:]*)::?".r

    def unapply(tree: Tree): Option[(Group, Artifact, Term)] =
      tree match {
        // Extracts: ivy"group::artifact:$version"
        case Term.Interpolate(
              Term.Name("ivy"),
              Lit.String(artifactWithoutVersion(group, artifact)) :: _,
              List(arg)
            ) =>
          Some((Group(group), Artifact(artifact), arg))
        // Extracts: ivy"group::artifact:version"
        case Term.Interpolate(
              Term.Name("ivy"),
              Lit.String(artifact(group, artifact, version)) :: _,
              _
            ) =>
          Some((Group(group), Artifact(artifact), Lit.String(version)))
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

  private object SbtVersionFile {
    def unapply(sourceFile: SourceFile): Option[(Version, Int)] =
      if (sourceFile.isPropertiesFile) {
        val parts = sourceFile.content.split("=").map(_.trim)
        parts.headOption match {
          case Some(label) if label == "sbt.version" =>
            parts.drop(1).headOption.map { version =>
              Version(version) -> sourceFile.content.indexOf(version)
            }

          case _ => None
        }
      } else {
        None
      }
  }
}
