package dependencies

import dependencies.cli.{CliApp, CliState, DependencyState}
import dependencies.versions.Versions
import tui.TUI
import view.View
import zio._

object Main extends ZIOAppDefault {
  val run = {
    for {
      options <- ZIO.serviceWithZIO[DependencyUpdater](_.allUpdateOptions)
      toUpdate = options
                   .filter(_._2.isNonEmpty)
                   .groupBy(_._1.location)
                   .values
                   .map { depsWithOpts =>
                     val opts: UpdateOptions = depsWithOpts.head._2
                     val deps: NonEmptyChunk[DependencyWithLocation] =
                       NonEmptyChunk.fromIterableOption(depsWithOpts.map(_._1)).get
                     DependencyState.from(deps, opts)
                   }
      chosen             <- CliApp.run(CliState(Chunk.from(toUpdate), 0, Set.empty))
      _                  <- ZIO.serviceWithZIO[DependencyUpdater](_.runUpdates(chosen))
      longestArtifactName = chosen.map(_._1.dependency.artifact.value.length).max
      longestVersion      = chosen.map(_._1.dependency.version.value.length).max
      _ <- ZIO.debug(
             View
               .vertical(
                 Chunk(
                   View.text("UPDATED DEPENDENCIES").blue,
                   View.text("────────────────────").blue.dim
                 ) ++
                   chosen.map { case (dep, version) =>
                     View.horizontal(
                       View.text(dep.dependency.artifact.value.padTo(longestArtifactName, ' ')).cyan,
                       View.text(dep.dependency.version.value.padTo(longestVersion, ' ')).cyan.dim,
                       View.text("→").cyan.dim,
                       View.text(version.value).green.underlined
                     )
                   }: _*
               )
               .padding(1)
               .renderNow
           )
    } yield ()
  }.provide(
    TUI.live(false),
    DependencyUpdater.live,
    Versions.live,
    Files.live
  )
}
