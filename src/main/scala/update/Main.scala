package update
import update.services.*
import update.services.dependencies.*
import update.model.*
import zio.*
import terminus.*
import terminus.KeyCode.{Character, Down}
import update.model.Dependency
import zio.stream.ZStream
import terminus.components.ScrollList
import update.utils.Rewriter

import scala.annotation.tailrec

final case class DependencyState(
    dependencies: NonEmptyChunk[Dependency],
    sourceInfo: SourceInfo,
    currentVersion: Version,
    updateOptions: UpdateOptions,
    selectedVersionType: VersionType
):
  @tailrec
  def nextVersion: DependencyState =
    val nextVersionType = selectedVersionType.next
    val next            = copy(selectedVersionType = nextVersionType)
    if updateOptions.hasVersionType(nextVersionType) then next
    else next.nextVersion

  @tailrec
  def previousVersion: DependencyState =
    val previousVersionType = selectedVersionType.prev
    val previous            = copy(selectedVersionType = previousVersionType)
    if updateOptions.hasVersionType(previousVersionType) then previous
    else previous.previousVersion

  def selectedVersion: Version =
    selectedVersionType match
      case VersionType.Major      => updateOptions.major.get
      case VersionType.Minor      => updateOptions.minor.get
      case VersionType.Patch      => updateOptions.patch.get
      case VersionType.PreRelease => updateOptions.preRelease.get

object DependencyState:
  def from(dependency: WithVersions[WithSource[Dependency]]): Option[DependencyState] =
    val dep        = dependency.value.value
    val versions   = dependency.versions
    val current    = dep.version
    val options    = UpdateOptions.getOptions(current, versions)
    val sourceInfo = dependency.value.sourceInfo
    val maybeVersionType = options match
      case UpdateOptions(Some(_), _, _, _) => Some(VersionType.Major)
      case UpdateOptions(_, Some(_), _, _) => Some(VersionType.Minor)
      case UpdateOptions(_, _, Some(_), _) => Some(VersionType.Patch)
      case UpdateOptions(_, _, _, Some(_)) => Some(VersionType.PreRelease)
      case _                               => None
    maybeVersionType.map(versionType => DependencyState(NonEmptyChunk(dep), sourceInfo, current, options, versionType))

object ScalaUpdateCLI extends TerminalAppZIO[List[DependencyState]]:
  enum Message:
    case LoadDependencies(dependencies: List[WithSource[Dependency]])
    case LoadVersions(dependency: WithVersions[WithSource[Dependency]])

  sealed trait State
  object State:
    case object Loading extends State
    case class Loaded(
        totalDependencyCount: Int,
        totalFinishedCount: Int,
        dependencies: List[DependencyState],
        selected: Set[SourceInfo],
        currentIndex: Int,
        showGroup: Boolean = false
    ) extends State:
      def moveUp: Loaded =
        copy(currentIndex = currentIndex - 1).clampIndex

      def moveDown: Loaded =
        copy(currentIndex = currentIndex + 1).clampIndex

      def nextVersion: Loaded =
        val newDependencies = dependencies.updated(currentIndex, dependencies(currentIndex).nextVersion)
        copy(dependencies = newDependencies)

      def previousVersion: Loaded =
        val newDependencies = dependencies.updated(currentIndex, dependencies(currentIndex).previousVersion)
        copy(dependencies = newDependencies)

      def incrementFinishedCount: Loaded =
        copy(totalFinishedCount = totalFinishedCount + 1)

      def withVersions(dep: WithVersions[WithSource[Dependency]]): Loaded =
        DependencyState.from(dep) match
          case Some(state) =>
            // if existing with same sourceInfo, append dependency
            // otherwise add new state
            val updated = dependencies.find(_.sourceInfo == state.sourceInfo) match
              case Some(existing) =>
                val newDependencies = existing.dependencies ++ state.dependencies
                dependencies.updated(dependencies.indexOf(existing), existing.copy(dependencies = newDependencies))
              case None =>
                dependencies.appended(state)
            copy(dependencies = updated.sortBy(_.dependencies.head))

          case None =>
            this

      def toggleCurrent: Loaded =
        if selected.contains(currentSourceInfo) then copy(selected = selected - currentSourceInfo)
        else copy(selected = selected + currentSourceInfo)

      def currentSourceInfo: SourceInfo =
        dependencies(currentIndex).sourceInfo

      def selectedStates: List[DependencyState] =
        dependencies.filter(dep => selected.contains(dep.sourceInfo))

      // if not all selected, select all
      // if all selected, deselect all
      def toggleAll: Loaded =
        if selected.size == dependencies.size then copy(selected = Set.empty)
        else copy(selected = dependencies.map(_.sourceInfo).toSet)

      private def clampIndex: Loaded =
        val nextSelected =
          if currentIndex < 0 then dependencies.length - 1
          else if currentIndex >= dependencies.length then 0
          else currentIndex
        copy(currentIndex = nextSelected)
    end Loaded
  end State

  def renderDependency(
      state: State.Loaded,
      firstColumnWidth: Int,
      maxCurrentVersionWidth: Int,
      dependencyState: DependencyState,
      index: Int
  ): View =
    val dependencies = dependencyState.dependencies
    val sourceInfo   = dependencyState.sourceInfo
    val isCurrent    = state.currentIndex == index
    val isSelected   = state.selected.contains(sourceInfo)

    val selectedVersionType = dependencyState.selectedVersionType
//    val versions   = dependencyState.updateOptions.versions
    val updateOptions = dependencyState.updateOptions
    def optionView(versionType: VersionType, version: Option[Version], color: Color = Color.Green) =
      version.map { version =>
        if versionType == selectedVersionType then
          View
            .text(version.toString)
            .color(color)
            .underline(isSelected)
        else View.text(version.toString).color(color).dim
      }

    val versionsView =
      View.horizontal(
        optionView(VersionType.Major, updateOptions.major),
        optionView(VersionType.Minor, updateOptions.minor),
        optionView(VersionType.Patch, updateOptions.patch),
        optionView(VersionType.PreRelease, updateOptions.preRelease, Color.Magenta),
        Option.when(isCurrent)(
          View.text(dependencyState.selectedVersionType.toString).yellow
        )
      )

    def renderSingle(dependency: Dependency) =
      View
        .horizontal(
          Option.when(state.showGroup)(
            Seq(
              View.text(dependency.group.value),
              View.text(if dependency.isJava then "%" else "%%").dim
            )
          ),
          View.text(dependency.artifact.value)
        )
        .width(firstColumnWidth)

    View
      .horizontal(
        if isCurrent then View.text("❯").bold else View.text(" ").dim,
        if isSelected then View.text("◉").bold.green else View.text("○").dim,
        View.vertical(
          dependencies.map(renderSingle).toList
        ),
        View
          .text(dependencyState.currentVersion.toString)
          .dim
          .width(maxCurrentVersionWidth),
        View.text("→").dim,
        versionsView
      )

  val divider: View =
    View
      .geometryReader { size =>
        View.text("─" * size.width)
      }
      .fillHorizontal
      .color(Color.Blue)

  def header(state: State) =
    val stats: View = state match
      case State.Loading =>
        View.text("Loading...")
      case loaded: State.Loaded =>
        View.horizontal(
          s"${loaded.totalFinishedCount}",
          View.text(s"/ ${loaded.totalDependencyCount} analyzed").dim,
          View.text("•").dim,
          View.text(s"${loaded.dependencies.flatMap(_.dependencies).length}"),
          View.text(s"updates").dim
        )
    View
      .geometryReader { size =>
        View
          .vertical(
            View.horizontal(
              View.text("SCALA UPDATE").bold,
              View.spacer,
              stats
            ),
            "─" * size.width
          )
      }
      .fillHorizontal
      .color(Color.Blue)

  //  space toggle  a toggle all  ↑/↓ move up/dow  g show groups  q quit

  def renderCommand(key: String, description: String): View =
    View.horizontal(View.text(key), View.text(description).dim).blue

  def renderCommands(loaded: State.Loaded) =
    View.horizontal(2)(
      renderCommand("space", "toggle"),
      renderCommand("a", "toggle all"),
      renderCommand("↑/↓", "up/down"),
      renderCommand("g", if loaded.showGroup then "hide groups" else "show groups"),
      renderCommand("q", "quit")
    )

  override def render(state: State): View =
    val body = state match
      case loaded: State.Loaded =>
        val maxGroupArtifactWidth = loaded.dependencies
          .flatMap(_.dependencies)
          .map { dep =>
            val group = if loaded.showGroup then s"${dep.group.value} ${if dep.isJava then "%" else "%%"} " else ""
            s"$group${dep.artifact.value}".length
          }
          .maxOption
          .getOrElse(0)

        val maxCurrentVersionWidth = loaded.dependencies
          .map(_.currentVersion.toString.length)
          .maxOption
          .getOrElse(0)

        View.vertical( //
          ScrollList(
            loaded.dependencies.zipWithIndex.map { (dep, index) =>
              renderDependency(loaded, maxGroupArtifactWidth, maxCurrentVersionWidth, dep, index)
            },
            loaded.currentIndex
          ).fillHorizontal,
          divider.dim,
          renderCommands(loaded)
        )

      case State.Loading =>
        View.text("Loading...")

    View.vertical(
      header(state),
      body
    )

  override def update(state: State, input: KeyCode | Message): Handled =
    state match
      case State.Loading =>
        input match
          case KeyCode.Exit | KeyCode.Character('q') =>
            Handled.Exit

          case Message.LoadDependencies(dependencies) =>
            Handled.Continue(State.Loaded(dependencies.length, 0, List.empty, Set.empty, 0))

          case _ => Handled.Continue(state)

      case state: State.Loaded =>
        input match
          case Message.LoadVersions(dep) =>
            Handled.Continue(state.withVersions(dep).incrementFinishedCount)

          case KeyCode.Up | Character('k') =>
            Handled.Continue(state.moveUp)
          case KeyCode.Down | Character('j') =>
            Handled.Continue(state.moveDown)
          case KeyCode.Right =>
            Handled.Continue(state.nextVersion)
          case KeyCode.Left =>
            Handled.Continue(state.previousVersion)
          case Character('g') =>
            Handled.Continue(state.copy(showGroup = !state.showGroup))
          case Character(' ') =>
            Handled.Continue(state.toggleCurrent)
          case Character('a') =>
            Handled.Continue(state.toggleAll)
          case KeyCode.Enter =>
            Handled.Done(state.selectedStates)
          case KeyCode.Exit | Character('q') =>
            Handled.Exit
          case _ =>
            Handled.Continue(state)

object Main extends ZIOAppDefault:

  val dependencyStream: ZStream[DependencyLoader, Throwable, List[WithSource[Dependency]]] =
    ZStream
      .fromZIO {
        ZIO.serviceWithZIO[DependencyLoader](_.getDependencies("."))
      }

  def loadVersionsStream(
      dependencies: List[WithSource[Dependency]]
  ): ZStream[Versions, Throwable, ScalaUpdateCLI.Message] =
    ZStream.fromIterable(dependencies).mapZIO { dep =>
      ZIO.serviceWithZIO[Versions](_.getVersions(dep.value)).map { versions =>
        ScalaUpdateCLI.Message.LoadVersions(WithVersions(dep, versions))
      }
    }

  val versionStream: ZStream[Versions & DependencyLoader, Throwable, ScalaUpdateCLI.Message] =
    dependencyStream
      .flatMap { dependencies =>
        ZStream.succeed(ScalaUpdateCLI.Message.LoadDependencies(dependencies)) merge
          loadVersionsStream(dependencies)
      }

  val program =
    for
      env <- ZIO.environment[Versions & DependencyLoader]
      selected <- ScalaUpdateCLI.run(
                    ScalaUpdateCLI.State.Loading,
                    versionStream.provideEnvironment(env).orDie
                  )
      _ <- ZIO.foreachDiscard(selected) { states =>
             val versionWithSource = states.map { state =>
               WithSource(state.selectedVersion, state.sourceInfo)
             }

             writeToSource(versionWithSource)
           }
    yield ()

  private def writeToSource(selectedVersions: List[WithSource[Version]]): Task[Unit] =
    val groupedBySourceFile = selectedVersions.groupBy(_.sourceInfo.path)
    ZIO.foreachParDiscard(groupedBySourceFile) { case (path, versions) =>
      rewriteSourceFile(path, versions)
    }

  private def rewriteSourceFile(
      path: String,
      versions: List[WithSource[Version]]
  ): Task[Unit] =
    for
      sourceCode <- ZIO.readFile(path)
      patches = versions.map { version =>
                  Rewriter.Patch(
                    start = version.sourceInfo.start,
                    end = version.sourceInfo.end,
                    replacement = version.value.toString
                  )
                }
      updatedSourceCode = Rewriter.rewrite(sourceCode, patches)
      _                <- ZIO.writeFile(path, updatedSourceCode)
    yield ()

  val run =
//    ZIO
//      .serviceWithZIO[ScalaUpdate](_.updateAllDependencies("."))
    program
      .provide(
        ScalaUpdate.layer,
        Versions.live,
        DependencyLoader.live,
        Files.live
      )
