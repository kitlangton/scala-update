package dependencies

import dependencies.versions.Versions
import tui.TerminalApp.Step
import zio._
import tui._
import view._
import tui.components.Choose

sealed trait VersionType extends Product with Serializable

object VersionType {
  case object Major      extends VersionType
  case object Minor      extends VersionType
  case object Patch      extends VersionType
  case object PreRelease extends VersionType
}

final case class DependencyState(
  location: Location,
  dependency: Dependency,
  versions: NonEmptyChunk[(VersionType, Version)],
  selectedIndex: Int
) {
  def selectedVersion: (VersionType, Version) = versions(selectedIndex)

  def rotateVersionRight: DependencyState =
    copy(selectedIndex = (selectedIndex + 1) min (versions.size - 1))

  def rotateVersionLeft: DependencyState =
    copy(selectedIndex = (selectedIndex - 1) max 0)

}

object DependencyState {
  def from(dep: DependencyWithLocation, options: UpdateOptions): DependencyState = {
    val versions =
      Chunk(
        options.major.map(VersionType.Major           -> _),
        options.minor.map(VersionType.Minor           -> _),
        options.patch.map(VersionType.Patch           -> _),
        options.preRelease.map(VersionType.PreRelease -> _)
      ).flatten

    DependencyState(dep.location, dep.dependency, NonEmptyChunk.fromIterable(versions.head, versions.tail), 0)
  }
}

final case class CliState(
  dependencies: Chunk[DependencyState],
  index: Int,
  selected: Set[Int]
) {

  def toggle: CliState = {
    val newSelected =
      if (selected(index)) selected - index
      else selected + index
    copy(selected = newSelected)
  }

  def toggleAll: CliState = {
    val newSelected =
      if (selected.isEmpty) dependencies.indices.toSet
      else Set.empty[Int]
    copy(selected = newSelected)
  }

  def moveUp: CliState =
    if (index == 0) this
    else copy(index = index - 1)

  def moveDown: CliState =
    if (index == dependencies.size - 1) this
    else copy(index = index + 1)

  def rotateVersionRight: CliState = {
    val newDependencies = dependencies.updated(index, dependencies(index).rotateVersionRight)
    copy(dependencies = newDependencies)
  }

  def rotateVersionLeft: CliState = {
    val newDependencies = dependencies.updated(index, dependencies(index).rotateVersionLeft)
    copy(dependencies = newDependencies)
  }

}

object CliApp extends TerminalApp[Nothing, CliState, Chunk[(DependencyWithLocation, Version)]] {
  override def render(state: CliState): View = {
    val longestArtifactLength = state.dependencies.map(_.dependency.artifact.value.length).max
    val longestVersionLength  = state.dependencies.map(_.versions.map(_._2.value.length).max).max
    val dependencies = state.dependencies.zipWithIndex.map { case (dep, idx) =>
      val selected: View =
        if (state.selected.contains(idx)) {
          View.text("◉").green
        } else {
          View.text("◯").cyan
        }

      val isActive = idx == state.index

      val cursor: View =
        if (isActive) {
          View.text("❯").cyan
        } else {
          View.text(" ")
        }

      val versions = dep.versions.toChunk.zipWithIndex.flatMap { case ((_, v), versionIdx) =>
        Chunk(
          if (versionIdx == dep.selectedIndex && state.selected.contains(idx)) {
            View.text(v.value).green.underlined
          } else if (versionIdx == dep.selectedIndex) {
            View.text(v.value).green
          } else {
            View.text(v.value).green.dim
          }
        )
      }

      val versionMode =
        if (dep.versions.size > 1 && isActive) View.text(dep.selectedVersion._1.toString).yellow
        else View.text("")

      View.horizontal(
        View.horizontal(0)(cursor, selected),
        View.text(dep.dependency.artifact.value.padTo(longestArtifactLength, ' ')).cyan,
        View.text(dep.dependency.version.value.padTo(longestVersionLength, ' ')).cyan.dim,
        View.text("⭢").cyan,
        View.horizontal((versions :+ versionMode): _*)
      )
    }

    val toggleKeybinding =
      if (state.dependencies(state.index).versions.size > 1) {
        View.horizontal(
          View.text("←/→").blue,
          View.text("toggle version").blue.dim
        )
      } else {
        View.text("")
      }

    val keybindings =
      View
        .horizontal(
          View.text("space").blue,
          View.text("toggle").blue.dim,
          " ",
          View.text("a").blue,
          View.text("toggle all").blue.dim,
          " ",
          View.text("↑/↓").blue,
          View.text("move up/down").blue.dim,
          " ",
          toggleKeybinding
        )
        .padding(top = 1)

    View
      .vertical(
        Chunk(
          View.text("SBT INTERACTIVE UPDATE").blue,
          View.text("──────────────────────").blue.dim
        ) ++
          dependencies ++
          Chunk(
            keybindings
          ): _*
      )
      .padding(1)
  }

  override def update(
    state: CliState,
    event: TerminalEvent[Nothing]
  ): TerminalApp.Step[CliState, Chunk[(DependencyWithLocation, Version)]] =
    event match {
      case TerminalEvent.UserEvent(event) =>
        ???
      case TerminalEvent.SystemEvent(keyEvent) =>
        keyEvent match {
          case KeyEvent.Character(' ') =>
            Step.update(state.toggle)
          case KeyEvent.Character('a') =>
            Step.update(state.toggleAll)
          case KeyEvent.Enter =>
            val chosen: List[(DependencyWithLocation, Version)] =
              state.selected.toList.sorted.map { idx =>
                val dep = state.dependencies(idx)
                (DependencyWithLocation(dep.dependency, dep.location), dep.selectedVersion._2)
              }
            Step.succeed(Chunk.from(chosen))
          case KeyEvent.Up =>
            Step.update(state.moveUp)
          case KeyEvent.Down =>
            Step.update(state.moveDown)
          case KeyEvent.Right =>
            Step.update(state.rotateVersionRight)
          case KeyEvent.Left =>
            Step.update(state.rotateVersionLeft)
          case KeyEvent.Escape | KeyEvent.Exit | KeyEvent.Character('q') =>
            Step.succeed(Chunk.empty)
          case _ =>
            Step.update(state)
        }
    }
}

// TODO: Collapse same version into single select node:
//
//  ❯◯ zio          2.0.0-RC2 ⭢ 2.0.0 2.0.0-RC6 Major
//     zio-streams
//
object Main extends ZIOAppDefault {
  val run = {
    for {
      options <- ZIO.serviceWithZIO[DependencyUpdater](_.allUpdateOptions)
      deps = Chunk.from(options.filter(_._2.isNonEmpty)).map { case (dep, opt) =>
               DependencyState.from(dep, opt)
             }
      chosen <- CliApp.run(CliState(deps, 0, Set.empty))
      _      <- ZIO.serviceWithZIO[DependencyUpdater](_.runUpdates(chosen))
    } yield ()
  }.provide(
    TUI.live(false),
    DependencyUpdater.live,
    Versions.live,
    Files.live
  )
}
