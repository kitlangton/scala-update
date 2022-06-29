package update

import update.versions.Versions
import tui.TUI
import zio._

object Main extends ZIOAppDefault {

  val run =
    ZIO
      .serviceWithZIO[CLI](_.run)
      .provide(
        CLI.live,
        TUI.live(false),
        DependencyUpdater.live,
        Versions.live,
        Files.live
      )

}
