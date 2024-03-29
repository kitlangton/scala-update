package update.utils

import dotty.tools.*
import dotty.tools.dotc.ast.{Trees, tpd}
import dotty.tools.dotc.core.*
import dotty.tools.dotc.core.Phases.Phase
import dotty.tools.dotc.interactive.*
import dotty.tools.dotc.parsing.Parser
import dotty.tools.dotc.{CompilationUnit, Compiler, Driver, ast, core, reporting, typer, util}

import scala.collection.*
import scala.language.unsafeNulls

class UntypedInteractiveCompiler extends Compiler:
  override def phases: List[List[Phase]] = List(
    List(new Parser)
  )

import dotty.tools.dotc.ast.Trees.*
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.reporting.*
import dotty.tools.dotc.util.*
import dotty.tools.io.AbstractFile

import java.net.URI
import scala.language.unsafeNulls

/** A driver which simply parses and produces untyped Trees */
class ParsingDriver(val settings: List[String]) extends Driver:
  import tpd.*

  override def sourcesRequired: Boolean = false

  private val myInitCtx: Context =
    val rootCtx = initCtx.fresh.addMode(Mode.ReadPositions).addMode(Mode.Interactive)
    rootCtx.setSetting(rootCtx.settings.YretainTrees, true)
    rootCtx.setSetting(rootCtx.settings.YcookComments, true)
    rootCtx.setSetting(rootCtx.settings.YreadComments, true)
    val ctx = setup(settings.toArray, rootCtx) match
      case Some((_, ctx)) => ctx
      case None           => rootCtx
    ctx.initialize()(using ctx)
    ctx

  private var myCtx: Context = myInitCtx
  def currentCtx: Context    = myCtx

  private val compiler: Compiler = new UntypedInteractiveCompiler

  private val myOpenedFiles = new mutable.LinkedHashMap[URI, SourceFile]:
    override def default(key: URI) = NoSource

  private val myOpenedTrees = new mutable.LinkedHashMap[URI, List[SourceTree]]:
    override def default(key: URI) = Nil

  private val myCompilationUnits = new mutable.LinkedHashMap[URI, CompilationUnit]

  initialize()

  def run(uri: URI, sourceCode: String): List[Diagnostic] = run(uri, SourceFile.virtual(uri, sourceCode))

  def run(uri: URI, source: SourceFile): List[Diagnostic] =
    import typer.ImportInfo.*

    val previousCtx = myCtx
    try
      val reporter =
        new StoreReporter(null) with UniqueMessagePositions with HideNonSensicalMessages

      val run = compiler.newRun(using myInitCtx.fresh.setReporter(reporter))
      myCtx = run.runContext.withRootImports

      given Context = myCtx

      myOpenedFiles(uri) = source

      run.compileSources(List(source))
      run.printSummary()
      val ctxRun = ctx.run.nn
      val unit   = if ctxRun.units.nonEmpty then ctxRun.units.head else ctxRun.suspendedUnits.head
      val t      = unit.tpdTree
      myOpenedTrees(uri) = topLevelTrees(t, source)
      myCompilationUnits(uri) = unit
      myCtx = myCtx.fresh.setPhase(myInitCtx.base.typerPhase)

      reporter.removeBufferedMessages
    catch
      case _: FatalError =>
        myCtx = previousCtx
        close(uri)
        Nil

  def close(uri: URI): Unit =
    myOpenedFiles.remove(uri)
    myOpenedTrees.remove(uri)
    myCompilationUnits.remove(uri)

  private def topLevelTrees(topTree: Tree, source: SourceFile): List[SourceTree] =
    val trees = new mutable.ListBuffer[SourceTree]

    def addTrees(tree: Tree): Unit = tree match
      case PackageDef(_, stats) =>
        stats.foreach(addTrees)
      case imp: Import =>
        trees += SourceTree(imp, source)
      case tree: TypeDef =>
        trees += SourceTree(tree, source)
      case _ =>
    addTrees(topTree)

    trees.toList

  /** Initialize this driver and compiler.
    *
    * This is necessary because an `InteractiveDriver` can be put to work
    * without having compiled anything (for instance, resolving a symbol coming
    * from a different compiler in this compiler). In those cases, an
    * un-initialized compiler may crash (for instance if late-compilation is
    * needed).
    */
  private def initialize(): Unit =
    val run = compiler.newRun(using myInitCtx.fresh)
    myCtx = run.runContext
    run.compileUnits(Nil, myCtx)
