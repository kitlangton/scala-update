package update

import zio.{Chunk, ChunkBuilder}
import zio.nio.file.Path

import scala.collection.mutable
import scala.meta._
import scala.util.chaining.scalaUtilChainingOps

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

sealed trait SourceFile {
  val path: Path
  val content: String
}
object SourceFile {
  final case class BuildPropertiesSourceFile(path: Path, content: String) extends SourceFile {
    import org.apache.commons.configuration2.PropertiesConfiguration
    import java.io.{ByteArrayInputStream, InputStreamReader}

    lazy val propertiesConfiguration: PropertiesConfiguration =
      (new PropertiesConfiguration).tap { pc =>
        pc.read(new InputStreamReader(new ByteArrayInputStream(content.getBytes)))
      }
  }

  final case class Sbt1DialectSourceFile(path: Path, content: String) extends SourceFile {
    import scala.meta._
    lazy val tree: Tree = dialects.Sbt1(content).parse[Source].get
  }
}

object DependencyParser {
  def getDependencies(sources: Chunk[SourceFile]): Chunk[DependencyWithLocation] = {
    lazy val nonSbtVersionDefs = collectNonSbtVersionDefs(sources)
    val builder                = ChunkBuilder.make[DependencyWithLocation]()

    sources.foreach {
      case source @ SbtVersionFile(version, start) =>
        val dependency = Dependency.sbt(version.value)
        val location   = Location(source.path, start, start + version.value.length)
        builder += DependencyWithLocation(dependency, location)

      case source: SourceFile.Sbt1DialectSourceFile =>
        source.tree.traverse {
          // Matches String Literal Versions: "zio.dev" %% "zio" % "1.0.0"
          case GroupAndArtifact(group, artifact, term @ Lit.String(version)) =>
            val location   = Location(path = source.path, start = term.pos.start, end = term.pos.end)
            val dependency = Dependency(group, artifact, Version(version))
            builder += DependencyWithLocation(dependency, location)

          // Matches Identifier Versions: "zio.dev" %% "zio" % zioVersion
          //                              "zio.dev" %% "zio" % V.zio
          case GroupAndArtifact(group, artifact, GetIdentifier(name)) if nonSbtVersionDefs.contains(name) =>
            val versionDef = nonSbtVersionDefs(name)
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
          case MillGroupArtifact(group, artifact, GetIdentifier(name)) if nonSbtVersionDefs.contains(name) =>
            val versionDef = nonSbtVersionDefs(name)
            val location   = versionDef.location
            val dependency = Dependency(group, artifact, versionDef.version)
            builder += DependencyWithLocation(dependency, location)
        }

      case _ =>
    }

    builder.result()
  }

  private[update] def collectNonSbtVersionDefs(sourceFiles: Chunk[SourceFile]): Map[String, VersionWithLocation] =
    sourceFiles.collect { case f: SourceFile.Sbt1DialectSourceFile => parseVersionDefs(f) }.flatten.toMap

  private[update] def parseVersionDefs(
    sourceFile: SourceFile.Sbt1DialectSourceFile
  ): Map[String, VersionWithLocation] = {
    val mutableMap = mutable.Map.empty[String, VersionWithLocation]
    sourceFile.tree.traverse { case q"""val $identifier = ${term @ Lit.String(versionString)}""" =>
      val location            = Location(sourceFile.path, term.pos.start, term.pos.end)
      val versionWithLocation = VersionWithLocation(Version(versionString), location)
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

        // { <identifier> }
        case Term.Block(List(GetIdentifier(identifier))) => Some(identifier)

        case _ => None
      }
  }

  private object SbtVersionFile {
    def unapply(sourceFile: SourceFile.BuildPropertiesSourceFile): Option[(Version, Int)] =
      Option(sourceFile.propertiesConfiguration.getProperty("sbt.version")).map { _version =>
        val version = _version.asInstanceOf[String]
        Version(version) -> sourceFile.content.indexOf(version)
      }
  }
}
