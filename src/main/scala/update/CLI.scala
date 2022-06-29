package update

import tui.TUI
import update.cli.{CliApp, CliState, DependencyState}
import view.View
import zio._

final case class CLI(dependencyUpdater: DependencyUpdater, tui: TUI) {

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

  val run: IO[Throwable, Unit] = {
    for {
      options0 <- dependencyUpdater.allUpdateOptions
      options   = options0.filter(_._2.isNonEmpty)
      _ <- if (options.nonEmpty) runSelector(options)
           else ZIO.debug(dependenciesUpToDateMessage)
    } yield ()
  }.catchSome { //
    case AppError.MissingBuildSbt =>
      ZIO.debug(missingBuildSbtErrorMessage)
  }

  def runSelector(
    options: Chunk[(DependencyWithLocation, UpdateOptions)]
  ): IO[Throwable, Unit] = {

    // TODO: Refactor
    // Groups dependencies by their location
    val grouped: Chunk[DependencyState] =
      Chunk
        .from(options.groupBy(_._1.location).values.map { depsWithOpts =>
          val opts: UpdateOptions = depsWithOpts.head._2
          val deps: NonEmptyChunk[DependencyWithLocation] =
            NonEmptyChunk.fromIterableOption(depsWithOpts.map(_._1)).get
          DependencyState.from(deps, opts)
        })
        .sortBy(_.dependencies.head.artifact.value)

    for {
      selectedDeps <- CliApp.run(CliState(grouped, 0, Set.empty)).provideEnvironment(ZEnvironment(tui))
      _ <- ZIO.when(selectedDeps.nonEmpty) {
             for {
               _ <- dependencyUpdater.runUpdates(selectedDeps)
               _ <- displayUpdateSuccessMessage(selectedDeps)
             } yield ()
           }
    } yield ()
  }

  private def displayUpdateSuccessMessage(selectedDeps: Chunk[(DependencyWithLocation, Version)]): UIO[Unit] = {
    // TODO: Render Group as well
    val longestArtifactName = selectedDeps.map(_._1.dependency.artifact.value.length).max
    val longestVersion      = selectedDeps.map(_._1.dependency.version.value.length).max
    val depViews =
      selectedDeps.map { case (dep, version) =>
        View.horizontal(
          View.text(dep.dependency.artifact.value.padTo(longestArtifactName, ' ')).cyan,
          View.text(dep.dependency.version.value.padTo(longestVersion, ' ')).cyan.dim,
          View.text("→").cyan.dim,
          View.text(version.value).green.underlined
        )
      }

    ZIO.debug(
      View
        .vertical(
          Chunk(
            View.text("UPDATED DEPENDENCIES").blue,
            View.text("────────────────────").blue.dim
          ) ++ depViews: _*
        )
        .padding(1)
        .renderNow
    )
  }

  private lazy val dependenciesUpToDateMessage: String =
    View
      .text("All of your dependencies are up to date! 🎉")
      .blue
      .padding(1)
      .renderNow

  private lazy val missingBuildSbtErrorMessage: String =
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
}

object CLI {
  val live: ZLayer[DependencyUpdater with TUI, Nothing, CLI] =
    ZLayer.fromFunction(apply _)
}
