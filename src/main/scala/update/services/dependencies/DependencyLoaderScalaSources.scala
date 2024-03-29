package update.services.dependencies

import coursierapi.{Cache, Fetch}
import dotty.tools.dotc.ast.Trees.{Tree, Untyped}
import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.util.SourceFile
import dotty.tools.io.AbstractFile
import update.model.Dependency
import update.services.Files
import update.utils.ParsingDriver
import zio.*
import zio.nio.file.Path

import java.io.File
import scala.collection.mutable.ListBuffer
import scala.io.Codec
import scala.jdk.CollectionConverters.*

/** This class uses a parser-only instance of the Dotty compiler to parse Scala
  * build sources (.sbt files, project/ files, mill's .sc files, etc) and
  * extract dependencies from them.
  */
final case class DependencyLoaderScalaSources(files: Files) extends DependencyLoader:
  private val fetch = Fetch.create().withCache(Cache.create())
  fetch.addDependencies(coursierapi.Dependency.of("org.scala-lang", "scala3-library_3", "3.4.0"))
  private val extraLibraries = fetch.fetch().asScala.map(_.toPath()).toSeq
  private val driver = new ParsingDriver(
    List("-color:never", "-classpath", extraLibraries.mkString(File.pathSeparator))
  )
  private given ctx: Context = driver.currentCtx

  def getDependencies(root: String): Task[List[WithSource[Dependency]]] =
    for paths <- files.allBuildScalaPaths(root)
    yield
      val allTrees = ListBuffer.empty[untpd.Tree]
      paths.foreach { path =>
        loadPath(path)
        val trees: List[Tree[Untyped]] = driver.currentCtx.run.units.map(_.untpdTree)
        allTrees ++= trees
      }
      DependencyTraverser.getDependencies(allTrees.toList)

  private def loadPath(path: Path): Unit =
    val file     = AbstractFile.getFile(path.toFile.toPath)
    var contents = new String(file.toByteArray, Codec.UTF8.charSet)

    // NOTE: Until I figure out how to allow toplevel definitions,
    //   I'm wrapping the contents in a $$wrapper object.
    val pathString = path.toString
    if pathString.endsWith(".sbt") || pathString.endsWith(".sc") then contents = s"""object $$wrapper {\n$contents\n}"""

    val sourceFile  = SourceFile.virtual(path.toFile.toURI.toString, contents)
    val diagnostics = driver.run(path.toFile.toURI, sourceFile)
    diagnostics.foreach(println)

object DependencyLoaderScalaSources:
  val layer = ZLayer.fromFunction(DependencyLoaderScalaSources.apply)
