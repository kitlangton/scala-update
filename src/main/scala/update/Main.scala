package update

import update.versions.Versions
import tui.TUI
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
