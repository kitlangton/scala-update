package dependencies

import dependencies.cli.{CliApp, CliState, DependencyState}
import dependencies.versions.Versions
import tui.TUI
import view.View
import zio._

object Main extends ZIOAppDefault {

  val run = {
    for {
      options0 <- ZIO.serviceWithZIO[DependencyUpdater](_.allUpdateOptions)
      options   = options0.filter(_._2.isNonEmpty)
      _ <- if (options.nonEmpty)
             runSelector(options)
           else
             ZIO.debug(
               View
                 .text("All of your dependencies are up to date! 🎉")
                 .blue
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

  def runSelector(
    options: List[(DependencyWithLocation, UpdateOptions)]
  ): ZIO[DependencyUpdater with TUI, Throwable, Unit] = {
    val toUpdate =
      options.groupBy(_._1.location).values.map { depsWithOpts =>
        val opts: UpdateOptions = depsWithOpts.head._2
        val deps: NonEmptyChunk[DependencyWithLocation] =
          NonEmptyChunk.fromIterableOption(depsWithOpts.map(_._1)).get
        DependencyState.from(deps, opts)
      }

    for {
      chosen <- CliApp.run(CliState(Chunk.from(toUpdate), 0, Set.empty))
      _ <- ZIO.when(chosen.nonEmpty) {
             for {
               _                  <- ZIO.serviceWithZIO[DependencyUpdater](_.runUpdates(chosen))
               longestArtifactName = chosen.map(_._1.dependency.artifact.value.length).max
               longestVersion      = chosen.map(_._1.dependency.version.value.length).max
               _ <- Console
                      .printLine(
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
           }
    } yield ()
  }
}
