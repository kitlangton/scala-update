package update

import tui.TUI
import update.versions.Versions
import tui.view.{VerticalAlignment, View}
import zio._

sealed trait Subcommand extends Product with Serializable

object Subcommand {
  case object Interactive                extends Subcommand
  final case class Search(query: String) extends Subcommand

  def parse(input: List[String]): Option[Subcommand] =
    input match {
      case Nil =>
        Some(Interactive)
      case "search" :: query :: Nil =>
        Some(Search(query))
      case _ =>
        None
    }
}

object Main extends ZIOAppDefault {

  val run = {
    for {
      args      <- getArgs
      subcommand = Subcommand.parse(args.toList)
      _ <- subcommand match {
             case Some(Subcommand.Interactive) =>
               runInteractive
             case Some(Subcommand.Search(query)) =>
               runSearch(query)
             case None =>
               helpMessage
           }
    } yield ()
  }

  lazy val runInteractive =
    ZIO
      .serviceWithZIO[CLI](_.run)
      .provide(
        CLI.live,
        TUI.live(false),
        DependencyUpdater.live,
        Versions.live,
        Files.live
      )

  private def runSearch(query: String) =
    search.Search().searchCLI(query)

  lazy val helpMessage = {
    val view =
      View
        .vertical(
          View.text("SCALA UPDATE COMMANDS").blue,
          View.text("─────────────────────").blue.dim,
          "",
          View.horizontal(1, VerticalAlignment.top)(
            View.text("1.").dim,
            View.vertical(
              View.text("scala-update").blue.bold,
              View.text("Interactively update your library dependencies.").dim
            )
          ),
          "",
          View.horizontal(1, VerticalAlignment.top)(
            View.text("2.").dim,
            View.vertical(
              View.horizontal(
                View.text("scala-update").blue.dim,
                View.text("search").blue.bold,
                View.text("<query>").blue
              ),
              View.text("Search for Maven-hosted libraries.").dim
            )
          )
        )
        .padding(1)

    ZIO.debug(view.render(80, 10))
  }
}
