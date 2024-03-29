package update.services.dependencies

import update.model.Dependency
import update.services.*
import zio.*

object DependencyLoader:
  val live: ZLayer[Files, Nothing, DependencyLoader] =
    for
      sbtLoader   <- DependencyLoaderSbtVersion.layer
      scalaLoader <- DependencyLoaderScalaSources.layer
    yield ZEnvironment(DependencyLoaderCombined(List(sbtLoader.get, scalaLoader.get)))

  def getDependencies(root: String): ZIO[DependencyLoader, Throwable, List[WithSource[Dependency]]] =
    ZIO.serviceWithZIO(_.getDependencies(root))

trait DependencyLoader:
  def getDependencies(root: String): Task[List[WithSource[Dependency]]]
