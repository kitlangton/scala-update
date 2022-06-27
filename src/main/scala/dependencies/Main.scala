package dependencies

import dependencies.cli.{CliApp, CliState, DependencyState}
import dependencies.versions.Versions
import tui.TUI
import zio._

object Main extends ZIOAppDefault {
  val run = {
    for {
      options <- ZIO.serviceWithZIO[DependencyUpdater](_.allUpdateOptions)
      toUpdate = options.filter(_._2.isNonEmpty).groupBy(_._1.location).values.map { depsWithOpts =>
                   val opts = depsWithOpts.head._2
                   val deps: NonEmptyChunk[DependencyWithLocation] =
                     NonEmptyChunk.fromIterableOption(depsWithOpts.map(_._1)).get
                   DependencyState.from(deps, opts)
                 }
      chosen <- CliApp.run(CliState(Chunk.from(toUpdate), 0, Set.empty))
      _      <- ZIO.serviceWithZIO[DependencyUpdater](_.runUpdates(chosen))
    } yield ()
  }.provide(
    TUI.live(false),
    DependencyUpdater.live,
    Versions.live,
    Files.live
  )
}
