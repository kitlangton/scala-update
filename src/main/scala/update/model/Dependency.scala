package update.model

final case class Group(value: String)    extends AnyVal
final case class Artifact(value: String) extends AnyVal

// group %% artifact % version
final case class Dependency(
    group: Group,
    artifact: Artifact,
    version: Version,
    isJava: Boolean = true
)

object Dependency:
  def apply(group: String, artifact: String, version: String, isJava: Boolean): Dependency =
    new Dependency(Group(group), Artifact(artifact), Version(version), isJava)

  def scalaVersion(versionString: String): Dependency =
    val version = Version(versionString)
    if version.majorVersion.contains(3)
    then Dependency(Group("org.scala-lang"), Artifact("scala3-library"), version)
    else Dependency(Group("org.scala-lang"), Artifact("scala-library"), version)

  implicit val dependencyOrder: Ordering[Dependency] =
    Ordering.by(d => (d.group.value, d.artifact.value, d.version))

  def sbt(version: String): Dependency = Dependency(sbtGroup, sbtArtifact, Version(version))

  private val sbtGroup: Group                          = Group("org.scala-sbt")
  private val sbtArtifact: Artifact                    = Artifact("sbt")
  def isSbt(group: Group, artifact: Artifact): Boolean = group == sbtGroup && artifact == sbtArtifact
