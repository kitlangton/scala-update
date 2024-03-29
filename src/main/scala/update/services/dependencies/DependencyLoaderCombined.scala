package update.services.dependencies

import update.model.Dependency
import zio.*

final case class DependencyLoaderCombined(
    loaders: List[DependencyLoader]
) extends DependencyLoader:
  def getDependencies(root: String): Task[List[WithSource[Dependency]]] =
    for dependencies <- ZIO.foreachPar(loaders)(_.getDependencies(root))
    yield dependencies.flatten
