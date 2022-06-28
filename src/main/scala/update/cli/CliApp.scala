package update.cli

import update._
import tui.TerminalApp.Step
import tui._
import view._
import zio._

sealed trait VersionType extends Product with Serializable

object VersionType {
  case object Major      extends VersionType
  case object Minor      extends VersionType
  case object Patch      extends VersionType
  case object PreRelease extends VersionType
}

final case class DependencyState(
  location: Location,
  dependencies: NonEmptyChunk[Dependency],
  versions: NonEmptyChunk[(VersionType, Version)],
  versionIndex: Int
) {
  def selectedVersion: (VersionType, Version) = versions(versionIndex)

  def nextVersion: DependencyState =
    copy(versionIndex = (versionIndex + 1) min (versions.size - 1))

  def prevVersion: DependencyState =
    copy(versionIndex = (versionIndex - 1) max 0)

}

object DependencyState {
  def from(deps: NonEmptyChunk[DependencyWithLocation], options: UpdateOptions): DependencyState = {
    val versions =
      Chunk(
        options.major.map(VersionType.Major           -> _),
        options.minor.map(VersionType.Minor           -> _),
        options.patch.map(VersionType.Patch           -> _),
        options.preRelease.map(VersionType.PreRelease -> _)
      ).flatten

    DependencyState(
      deps.head.location,
      deps.map(_.dependency),
      NonEmptyChunk.fromIterable(versions.head, versions.tail),
      0
    )
  }
}

final case class CliState(
  dependencies: Chunk[DependencyState],
  cursorIndex: Int = 0,
  selected: Set[Int] = Set.empty,
  showGroups: Boolean = false
) {

  def toggleShowGroups: CliState =
    copy(showGroups = !showGroups)

  def toggle: CliState = {
    val newSelected =
      if (selected(cursorIndex)) selected - cursorIndex
      else selected + cursorIndex
    copy(selected = newSelected)
  }

  def toggleAll: CliState = {
    val newSelected =
      if (selected.isEmpty) dependencies.indices.toSet
      else Set.empty[Int]
    copy(selected = newSelected)
  }

  def moveUp: CliState =
    if (cursorIndex == 0) this
    else copy(cursorIndex = cursorIndex - 1)

  def moveDown: CliState =
    if (cursorIndex == dependencies.size - 1) this
    else copy(cursorIndex = cursorIndex + 1)

  def nextVersion: CliState = {
    val newDependencies = dependencies.updated(cursorIndex, dependencies(cursorIndex).nextVersion)
    copy(dependencies = newDependencies)
  }

  def prevVersion: CliState = {
    val newDependencies = dependencies.updated(cursorIndex, dependencies(cursorIndex).prevVersion)
    copy(dependencies = newDependencies)
  }

}

object CliApp extends TerminalApp[Nothing, CliState, Chunk[(DependencyWithLocation, Version)]] {
  override def render(state: CliState): View = {
    val longestGroupLength    = state.dependencies.flatMap(_.dependencies.map(_.group.value.length)).max
    val longestArtifactLength = state.dependencies.flatMap(_.dependencies.map(_.artifact.value.length)).max
    val longestVersionLength  = state.dependencies.flatMap(_.dependencies.map(_.version.value.length)).max
    val dependencies = state.dependencies.zipWithIndex.map { case (dep, idx) =>
      val selected: View =
        if (state.selected.contains(idx)) {
          View.text("▣").green
        } else {
          View.text("☐").cyan.dim
        }

      val isActive = idx == state.cursorIndex

      val cursor: View =
        if (isActive) {
          View.text("❯").cyan
        } else {
          View.text(" ")
        }

      val versions = dep.versions.toChunk.zipWithIndex.flatMap { case ((_, v), versionIdx) =>
        Chunk(
          if (versionIdx == dep.versionIndex && state.selected.contains(idx)) {
            View.text(v.value).green.underlined
          } else if (versionIdx == dep.versionIndex) {
            View.text(v.value).green
          } else {
            View.text(v.value).green.dim
          }
        )
      }

      val versionMode =
        if (dep.versions.size > 1 && isActive) View.text(dep.selectedVersion._1.toString).yellow
        else View.text("")

      val groupView = Chunk.from(Option.when(state.showGroups) {
        View.horizontal(
          View.text(dep.dependencies.head.group.value.padTo(longestGroupLength, ' ')).cyan,
          View.text("%%").cyan.dim
        )
      })

      View.horizontal(1, VerticalAlignment.top)(
        Chunk(
          View.horizontal(0)(cursor, selected)
        ) ++ groupView ++
          Chunk(
            View.vertical(
              dep.dependencies.map { dep =>
                View.text(dep.artifact.value.padTo(longestArtifactLength, ' ')).cyan
              }: _*
            )
          ) ++
          Chunk.from(Option.when(state.showGroups)(View.text("%").cyan.dim)) ++
          Chunk(
            View.text(dep.dependencies.head.version.value.padTo(longestVersionLength, ' ')).cyan.dim,
            View.text("→").cyan.dim,
            View.horizontal((versions :+ versionMode): _*)
          ): _*
      )
    }

    val toggleKeybinding =
      if (state.dependencies(state.cursorIndex).versions.size > 1) {
        View.horizontal(0)(
          "  ",
          View.text("←/→").blue,
          " ",
          View.text("toggle version").blue.dim
        )
      } else {
        View.text("")
      }

    val confirmBinding =
      if (state.selected.nonEmpty) {
        View.horizontal(0)(
          "  ",
          View.text("enter").blue,
          " ",
          View.text("update").blue.dim
        )
      } else {
        View.text("")
      }

    val keybindings =
      View
        .horizontal(0)(
          View.text("space").blue,
          " ",
          View.text("toggle").blue.dim,
          "  ",
          View.text("a").blue,
          " ",
          View.text("toggle all").blue.dim,
          "  ",
          View.text("↑/↓").blue,
          " ",
          View.text("move up/down").blue.dim,
          toggleKeybinding,
          confirmBinding,
          "  ",
          View.text("g").blue,
          " ",
          View.text(if (state.showGroups) "hide groups" else "show groups").blue.dim,
          "  ",
          View.text("q").blue,
          " ",
          View.text("quit").blue.dim
        )
        .padding(top = 1)

    View
      .vertical(
        Chunk(
          View.text("SCALA INTERACTIVE UPDATE").blue,
          View.text("────────────────────────").blue.dim
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
      case TerminalEvent.UserEvent(_) =>
        ???
      case TerminalEvent.SystemEvent(keyEvent) =>
        keyEvent match {
          case KeyEvent.Character(' ') =>
            Step.update(state.toggle)
          case KeyEvent.Character('a') =>
            Step.update(state.toggleAll)
          case KeyEvent.Enter =>
            val chosen: List[(DependencyWithLocation, Version)] =
              state.selected.toList.sorted.flatMap { idx =>
                val deps = state.dependencies(idx)
                deps.dependencies.map { dep =>
                  (DependencyWithLocation(dep, deps.location), deps.selectedVersion._2)
                }
              }
            Step.succeed(Chunk.from(chosen))
          case KeyEvent.Up | KeyEvent.Character('k') =>
            Step.update(state.moveUp)
          case KeyEvent.Down | KeyEvent.Character('j') =>
            Step.update(state.moveDown)
          case KeyEvent.Character('g') =>
            Step.update(state.toggleShowGroups)
          case KeyEvent.Right =>
            Step.update(state.nextVersion)
          case KeyEvent.Left =>
            Step.update(state.prevVersion)
          case KeyEvent.Escape | KeyEvent.Exit | KeyEvent.Character('q') =>
            Step.succeed(Chunk.empty)
          case _ =>
            Step.update(state)
        }
    }
}
