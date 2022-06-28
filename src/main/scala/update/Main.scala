package update

import update.cli.{CliApp, CliState, DependencyState}
import update.versions.Versions
import tui.TUI
import view.View
import zio._

/**
 *   - Get a list of the users current dependencies [[Dependency]]
 *     - Parse the users build.sbt [[DependencyParser]]
 *       - Use scala.meta to semantically parse the Scala AST
 *   - Find the available versions for each dependency [[Versions]]
 *     - Collect available newer versions across categories (major, minor,
 *       patch, pre-release) [[UpdateOptions]]
 *   - Display these options to the user, they select what they want.
 *   - Replace the versions in the source code.
 */
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
  }.catchSome { case AppError.MissingBuildSbt =>
    ZIO.debug(
      View
        .vertical(
          View.text("SCALA INTERACTIVE UPDATE ERROR").red,
          View.text("──────────────────────────────").red.dim,
          View.horizontal(
            "I could not find a",
            View.text("build.sbt").red.underlined,
            s"file in the current directory."
          ),
          View.text("Are you running this command from a valid sbt project root?").dim
        )
        .padding(1)
        .renderNow
    )

  } provide (
    TUI.live(false),
    DependencyUpdater.live,
    Versions.live,
    Files.live
  )

  def runSelector(
    options: Chunk[(DependencyWithLocation, UpdateOptions)]
  ): ZIO[DependencyUpdater with TUI, Throwable, Unit] = {
    val grouped =
      Chunk
        .from(options.groupBy(_._1.location).values.map { depsWithOpts =>
          val opts: UpdateOptions = depsWithOpts.head._2
          val deps: NonEmptyChunk[DependencyWithLocation] =
            NonEmptyChunk.fromIterableOption(depsWithOpts.map(_._1)).get
          DependencyState.from(deps, opts)
        })
        .sortBy(_.dependencies.head.artifact.value)

    for {
      chosen <- CliApp.run(CliState(grouped, 0, Set.empty))
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
