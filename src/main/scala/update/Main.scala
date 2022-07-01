package update

import tui.TUI
import update.versions.Versions
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
               ZIO
                 .serviceWithZIO[CLI](_.run)
                 .provide(
                   CLI.live,
                   TUI.live(false),
                   DependencyUpdater.live,
                   Versions.live,
                   Files.live
                 )
             case Some(Subcommand.Search(query)) =>
               search.Search().searchCLI(query)
             case None =>
               ZIO.debug("Unknown subcommand")
           }
    } yield ()
  }

}
