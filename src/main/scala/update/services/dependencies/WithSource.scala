package update.services.dependencies

import dotty.tools.dotc.ast.Trees.Tree
import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.Context

final case class SourceInfo(path: String, start: Int, end: Int):
  def shiftStart(by: Int): SourceInfo = copy(start = start + by)
  def shiftEnd(by: Int): SourceInfo   = copy(end = end + by)

final case class WithSource[A](value: A, sourceInfo: SourceInfo):
  def start: Int   = sourceInfo.start
  def end: Int     = sourceInfo.end
  def path: String = sourceInfo.path

  def shiftStart(by: Int): WithSource[A] = copy(sourceInfo = sourceInfo.shiftStart(by))
  def shiftEnd(by: Int): WithSource[A]   = copy(sourceInfo = sourceInfo.shiftEnd(by))

object WithSource:
  def fromTree[A](value: A, tree: untpd.Tree)(using Context): WithSource[A] =
    val path = tree.source.path
    // If the file allows top-level definitions (e.g., an .sbt or a .sc), then we need to
    // account for the addition of the `object $wrapper {\n` that's done in DependencyLoader.scala
    val shift = if path.endsWith(".sbt") || path.endsWith(".sc") then 18 else 0
    WithSource(
      value = value,
      sourceInfo = SourceInfo(
        path = path.stripPrefix("file:"),
        start = tree.sourcePos.span.start - shift + 1, // shift over by a 1 to account for the double quote char
        end = tree.sourcePos.span.end - shift - 1
      )
    )
