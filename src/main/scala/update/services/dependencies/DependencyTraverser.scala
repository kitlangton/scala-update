package update.services.dependencies

import dotty.tools.dotc.ast.{Trees, untpd}
import dotty.tools.dotc.ast.untpd.UntypedTreeTraverser
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.Context
import update.*
import update.model.Dependency
import update.services.*

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object DependencyTraverser:

  def getDependencies(trees: List[untpd.Tree])(using Context): List[WithSource[Dependency]] =
    traverseValDefs(trees)
    traverseDependencies(trees)
    dependencies.toList

  // Stores parsed version definitions, e.g.:
  // val zioVersion  = "version"
  private val defs: mutable.Map[String, (String, untpd.Tree)] = mutable.Map.empty

  private val dependencies: ListBuffer[WithSource[Dependency]] = ListBuffer.empty

  private def traverseValDefs(trees: List[untpd.Tree])(using Context): Unit =
    val traverser = new UntypedTreeTraverser:
      override def traverse(tree: untpd.Tree)(using Context): Unit =
        tree match
          // Matches: val ident = "version"
          case untpd.ValDef(Name(name), _, tree @ StringLiteral(version)) =>
            defs += (name -> (version, tree))

          // Matches: def ident = "version"
          case untpd.DefDef(Name(name), _, _, tree @ StringLiteral(version)) =>
            defs += (name -> (version, tree))

          case _ =>
            traverseChildren(tree)
    trees.foreach(traverser.traverse)

  private def traverseDependencies(trees: List[untpd.Tree])(using Context): Unit =
    val traverser = new UntypedTreeTraverser:
      override def traverse(tree: untpd.Tree)(using Context): Unit =
        tree match
          // Matches: scalaVersion := "3.4.0"
          case MatchScalaVersion(versionTree @ StringLiteral(version)) =>
            println(s"scala version = $version")
            dependencies += WithSource.fromTree(Dependency.scalaVersion(version), versionTree)

          // Matches: "groupId" %% "artifactId" % "version"
          case MatchDependency(group, artifact, versionTree @ StringLiteral(version), isJava) =>
            dependencies += WithSource.fromTree(Dependency(group, artifact, version, isJava), versionTree)

          // Matches: "groupId" %% "artifactId" % ident
          case MatchDependency(group, artifact, SelectOrIdent(ident), isJava) =>
            defs.get(ident).foreach { (version, tree) =>
              dependencies += WithSource.fromTree(Dependency(group, artifact, version, isJava), tree)
            }

          // ivy"dev.zio::zio:$zioVersion",
          case untpd.InterpolatedString(
                _,
                List(
                  Trees.Thicket(List(StringLiteral(MillGroupArtifact(group, artifact, colons)), SelectOrIdent(ident))),
                  _
                )
              ) =>
            defs.get(ident).foreach { (version, tree) =>
              dependencies += WithSource.fromTree(Dependency(group, artifact, version, colons == 1), tree)
            }

          case untpd.InterpolatedString(
                _,
                List(tree @ StringLiteral(MillGroupArtifactVersion(group, artifact, version, colons)))
              ) =>
            val withSource = WithSource.fromTree(Dependency(group, artifact, version, colons == 1), tree)

            // We want the position of the version, so we shift it by the length of the group + artifact + colons
            // group::artifact:version
            // >>>>>>>>>>>>>>>>
            dependencies += withSource.shiftStart(group.length + artifact.length + colons).shiftEnd(1)

          case tree =>
//            if tree.toString.contains("scalaVersion") then println(s"tree = ${tree}")
            traverseChildren(tree)
    trees.foreach(traverser.traverse)

////////////////
// Extractors //
////////////////

// Matches: group::artifact or group:artifact and returns the # of colons as well
object MillGroupArtifact:
  def unapply(string: String): Option[(String, String, Int)] =
    string match
      case s"$group::$artifact:"  => Some((group, artifact, 2))
      case s"$group:$artifact:"   => Some((group, artifact, 1))
      case s"$group:::$artifact:" => Some((group, artifact, 3))
      case _                      => None

object MillGroupArtifactVersion:
  def unapply(string: String): Option[(String, String, String, Int)] =
    string match
      case s"$group::$artifact:$version"  => Some((group, artifact, version, 2))
      case s"$group:$artifact:$version"   => Some((group, artifact, version, 1))
      case s"$group:::$artifact:$version" => Some((group, artifact, version, 3))
      case _                              => None

// Matches either lhs.<ident> or just <ident>
object SelectOrIdent:
  def unapply(tree: untpd.Tree)(using Context): Option[String] = tree match
    case block: Trees.Block[?] if block.stats.isEmpty => SelectOrIdent.unapply(block.expr)
    case untpd.Select(_, Name(name))                  => Some(name)
    case untpd.Ident(Name(name))                      => Some(name)
    case _                                            => None

// match scala version assignment
// matches: scalaVersion := "3.4.0"
object MatchScalaVersion:
  def unapply(tree: untpd.Tree)(using Context): Option[untpd.Tree] =
    tree match
      case untpd.InfixOp(Ident("scalaVersion"), Ident(":="), versionTree) =>
        Some(versionTree)
      case untpd.InfixOp(
            untpd.InfixOp(Ident("ThisBuild"), Ident("/"), Ident("scalaVersion")),
            Ident(":="),
            versionTree
          ) =>
        Some(versionTree)
      case _ =>
        None

// matcher for: "groupId" %% "artifactId" % tree
object MatchDependency:
  def unapply(tree: untpd.Tree)(using Context): Option[(String, String, untpd.Tree, Boolean)] =
    tree match
      case untpd.InfixOp(
            untpd.InfixOp(StringLiteral(group), Ident(percents @ ("%%%" | "%%" | "%")), StringLiteral(artifact)),
            Ident("%"),
            version
          ) =>
        val isJava = percents == "%"
        Some((group, artifact, version, isJava))
      case _ => None

object Name:
  def unapply(name: dotty.tools.dotc.core.Names.Name): Some[String] =
    Some(name.toString)

object Ident:
  def unapply(tree: untpd.Tree): Option[String] = tree match
    case untpd.Ident(Name(name)) => Some(name)
    case _                       => None

object StringLiteral:
  def unapply(tree: untpd.Tree): Option[String] = tree match
    case untpd.Literal(constant: Constant) => Some(constant.stringValue)
    case _                                 => None
