package tech.zakomi.zakdroid.ui.workbench

import android.content.res.Resources
import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tech.zakomi.zakdroid.MainActivity
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.designsystem.ZakomiLogo
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.core.workspace.WorkspaceKind
import com.termux.view.TerminalView
import tech.zakomi.zakdroid.core.settings.AppSettings
import tech.zakomi.zakdroid.core.settings.AutoSave
import tech.zakomi.zakdroid.core.settings.SidePosition
import tech.zakomi.zakdroid.core.settings.ThemeId
import tech.zakomi.zakdroid.editor.ui.LineCommand
import tech.zakomi.zakdroid.ui.editor.EditorActions
import tech.zakomi.zakdroid.git.GitException
import tech.zakomi.zakdroid.ui.git.GitOperation
import tech.zakomi.zakdroid.ui.git.SourceControlEvent
import tech.zakomi.zakdroid.ui.git.SourceControlPane
import tech.zakomi.zakdroid.ui.git.SourceControlViewModel
import tech.zakomi.zakdroid.ui.palette.QuickPick
import tech.zakomi.zakdroid.ui.palette.QuickPickMode
import tech.zakomi.zakdroid.ui.palette.WorkbenchCommand
import tech.zakomi.zakdroid.ui.search.SearchPane
import tech.zakomi.zakdroid.ui.search.SearchViewModel
import tech.zakomi.zakdroid.editor.document.DocumentLoader
import tech.zakomi.zakdroid.editor.document.LoadResult
import tech.zakomi.zakdroid.editor.ui.rememberEditorController
import tech.zakomi.zakdroid.state.OpenTab
import tech.zakomi.zakdroid.ui.editor.EditorArea
import tech.zakomi.zakdroid.ui.editor.EditorEvent
import tech.zakomi.zakdroid.ui.editor.EditorViewModel
import tech.zakomi.zakdroid.ui.explorer.ExplorerMessage
import tech.zakomi.zakdroid.ui.explorer.ExplorerPane
import tech.zakomi.zakdroid.ui.explorer.ExplorerViewModel
import tech.zakomi.zakdroid.ui.explorer.NewProjectDialog
import tech.zakomi.zakdroid.ui.explorer.WelcomePane
import tech.zakomi.zakdroid.ui.runtime.RuntimeSheet
import tech.zakomi.zakdroid.ui.runtime.RuntimeViewModel
import tech.zakomi.zakdroid.ui.terminal.TerminalPanel
import tech.zakomi.zakdroid.ui.terminal.TerminalViewModel
import java.io.File

/** The views of the Side Bar, chosen in the Activity Bar. */
enum class SideView { Explorer, Search, SourceControl }

/**
 * The VS Code-style workbench. Tablets (>= 600dp) show the Side Bar next to the
 * editor; phones show one pane and open the Explorer as a drawer.
 */
@Composable
fun WorkbenchScreen(
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenExtensions: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    explorerVm: ExplorerViewModel = hiltViewModel(),
    searchVm: SearchViewModel = hiltViewModel(),
    sourceControlVm: SourceControlViewModel = hiltViewModel(),
    editorVm: EditorViewModel = hiltViewModel(),
    terminalVm: TerminalViewModel = hiltViewModel(),
    runtimeVm: RuntimeViewModel = hiltViewModel(),
) {
    val colors = WorkbenchTheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isTablet = windowWidth >= 600.dp
    val snackbar = remember { SnackbarHostState() }
    val controller = rememberEditorController()

    val explorer by explorerVm.state.collectAsStateWithLifecycle()
    val search by searchVm.state.collectAsStateWithLifecycle()
    val sourceControl by sourceControlVm.state.collectAsStateWithLifecycle()
    var gitAccountRequest by remember { mutableIntStateOf(0) }
    val recent by explorerVm.recent.collectAsStateWithLifecycle()
    val projects by explorerVm.projects.collectAsStateWithLifecycle()
    val tabs by editorVm.tabs.collectAsStateWithLifecycle()
    val activeKey by editorVm.activeKey.collectAsStateWithLifecycle()
    val loading by editorVm.loading.collectAsStateWithLifecycle()
    val running by editorVm.running.collectAsStateWithLifecycle()
    val runtime by runtimeVm.state.collectAsStateWithLifecycle()
    val cursor by controller.cursor.collectAsState()

    var sideBarVisible by rememberSaveable { mutableStateOf(true) }
    var sideView by rememberSaveable { mutableStateOf(SideView.Explorer) }
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    var quickPick by remember { mutableStateOf<QuickPickMode?>(null) }
    var panelVisible by rememberSaveable { mutableStateOf(false) }
    var panelHeightDp by rememberSaveable { mutableFloatStateOf(280f) }
    var runtimeSheet by rememberSaveable { mutableStateOf<Boolean?>(null) } // null = hidden, true = opened for a run
    var newProject by rememberSaveable { mutableStateOf(false) }
    // Closing several unsaved tabs at once asks about them one after another.
    val confirmClose = remember { mutableStateListOf<OpenTab>() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "folder"
            explorerVm.openSafFolder(uri, name)
        }
    }

    // Project archives. The name of the project being exported outlives the picker; null = all projects.
    var exportProjectName by rememberSaveable { mutableStateOf<String?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) explorerVm.exportProjects(exportProjectName, uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) explorerVm.importProjects(uri, DocumentFile.fromSingleUri(context, uri)?.name.orEmpty())
    }
    val exportProjects = { projectName: String? ->
        // The archive is made from the files on disk, so unsaved edits are written first.
        scope.launch {
            if (editorVm.saveAllNow()) {
                exportProjectName = projectName
                exportPicker.launch("${projectName ?: "zakdroid-projects"}.zip")
            }
        }
        Unit
    }
    val importProjects = { importPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }

    // ---- Events ---------------------------------------------------------------------------
    val messages = rememberEventTexts()
    LaunchedEffect(Unit) {
        editorVm.events.collect { event ->
            when (event) {
                is EditorEvent.FileMissing -> snackbar.showSnackbar(messages.fileMissing(event.name))
                is EditorEvent.OpenFailed -> snackbar.showSnackbar(messages.openFailed(event.name, event.reason, event.detail))
                is EditorEvent.SaveFailed -> snackbar.showSnackbar(messages.saveFailed(event.name, event.detail))
                is EditorEvent.RunFailed -> snackbar.showSnackbar(messages.runFailed(event.name, event.detail))
                is EditorEvent.ConfirmClose -> if (confirmClose.none { it.key == event.tab.key }) confirmClose += event.tab
                is EditorEvent.Reveal -> editorVm.tabs.value.firstOrNull { it.key == event.key }?.let { tab ->
                    controller.reveal(tab.document, event.line, event.startColumn, event.endColumn)
                }
                EditorEvent.RuntimeNotReady -> runtimeSheet = true
                EditorEvent.NothingToRun -> snackbar.showSnackbar(messages.runNothing)
                is EditorEvent.UnsupportedRun -> snackbar.showSnackbar(messages.runNotSupported(event.language.displayName))
                EditorEvent.RunStarted -> panelVisible = true
            }
        }
    }
    LaunchedEffect(Unit) {
        sourceControlVm.events.collect { event ->
            when (event) {
                is SourceControlEvent.Done -> snackbar.showSnackbar(messages.gitDone(event.operation, event.detail))
                is SourceControlEvent.Failed -> snackbar.showSnackbar(messages.gitFailed(event.kind, event.detail))
                SourceControlEvent.IdentityNeeded -> {
                    gitAccountRequest++
                    snackbar.showSnackbar(messages.gitIdentityNeeded)
                }
                SourceControlEvent.FilesChanged -> {
                    editorVm.reloadAllFromDisk()
                    explorerVm.reload()
                }
                is SourceControlEvent.Cloned -> {
                    explorerVm.refreshProjects()
                    snackbar.showSnackbar(messages.gitDone(GitOperation.Clone, event.workspace.name))
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        explorerVm.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    is ExplorerMessage.Error -> messages.explorerError(message.text)
                    ExplorerMessage.SaveFolderFilesFirst -> messages.saveFolderFirst
                    ExplorerMessage.AccessLost -> messages.accessLost
                    is ExplorerMessage.Exported -> messages.projectsExported(message.projects)
                    ExplorerMessage.NothingToExport -> messages.nothingToExport
                    is ExplorerMessage.ExportFailed -> messages.exportFailed(message.detail)
                    is ExplorerMessage.Imported -> messages.projectsImported(message.names)
                    is ExplorerMessage.ImportFailed -> messages.importFailed(message.detail)
                },
            )
        }
    }

    // ---- Actions --------------------------------------------------------------------------
    val sideOpen = if (isTablet) sideBarVisible else drawerState.isOpen
    // Tapping the shown view's icon hides the Side Bar; tapping the other one switches to it.
    val toggleSide = { view: SideView ->
        val hide = sideOpen && sideView == view
        sideView = view
        if (isTablet) sideBarVisible = !hide else scope.launch { if (hide) drawerState.close() else drawerState.open() }
        Unit
    }
    val toggleExplorer = { toggleSide(SideView.Explorer) }
    val findInFiles = {
        controller.selectedTextOnOneLine()?.let(searchVm::setText)
        sideView = SideView.Search
        if (isTablet) sideBarVisible = true else scope.launch { drawerState.open() }
        searchFocusRequest++
    }
    val closeDrawer = { if (!isTablet) scope.launch { drawerState.close() } }
    val activeTab = tabs.firstOrNull { it.key == activeKey }
    val lineCommand = { command: LineCommand -> controller.run(command, activeTab?.document?.language?.lineComment); Unit }
    val zoom = { delta: Int ->
        onUpdateSettings { it.copy(editorFontSizeSp = if (delta == 0) AppSettings.DEFAULT_EDITOR_FONT_SIZE else it.editorFontSizeSp + delta) }
    }
    val toggleTerminal = {
        panelVisible = !panelVisible
        if (panelVisible && terminalVm.tabs.value.isEmpty()) terminalVm.newShell()
    }
    val openFile = { ref: FileRef, name: String, relative: String ->
        editorVm.open(ref, name, relative)
        if (!isTablet) scope.launch { drawerState.close() }
    }

    // ---- Hardware keyboard shortcuts (VS Code keymap subset for Phase 1) -------------------
    val activity = LocalActivity.current as? MainActivity
    val shortcutState = rememberUpdatedState { event: KeyEvent ->
        val ctrl = event.isCtrlPressed
        val shift = event.isShiftPressed
        val alt = event.isAltPressed
        val inEditor = controller.hasFocus
        // Ctrl+P, Ctrl+G and Ctrl+H belong to the shell while the terminal has focus.
        val inTerminal = activity?.currentFocus is TerminalView
        when {
            ctrl && shift && event.keyCode == KeyEvent.KEYCODE_P || event.keyCode == KeyEvent.KEYCODE_F1 -> { quickPick = QuickPickMode.Commands; true }
            ctrl && shift && event.keyCode == KeyEvent.KEYCODE_O -> { quickPick = QuickPickMode.Symbols; true }
            ctrl && shift && event.keyCode == KeyEvent.KEYCODE_F -> { findInFiles(); true }
            ctrl && shift && event.keyCode == KeyEvent.KEYCODE_E -> { if (!(sideOpen && sideView == SideView.Explorer)) toggleExplorer(); true }
            ctrl && shift && event.keyCode == KeyEvent.KEYCODE_T -> { editorVm.reopenClosed(); true }
            ctrl && shift && event.keyCode == KeyEvent.KEYCODE_K && inEditor -> { lineCommand(LineCommand.Delete); true }
            ctrl && !shift && event.keyCode == KeyEvent.KEYCODE_P && !inTerminal -> { quickPick = QuickPickMode.Files; true }
            ctrl && !shift && event.keyCode == KeyEvent.KEYCODE_G && !inTerminal -> { quickPick = QuickPickMode.Line; true }
            ctrl && !shift && event.keyCode == KeyEvent.KEYCODE_H && !inTerminal -> { controller.openSearch(withReplace = true); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_SLASH && inEditor -> { lineCommand(LineCommand.ToggleComment); true }
            alt && !ctrl && event.keyCode == KeyEvent.KEYCODE_DPAD_UP && inEditor -> { lineCommand(if (shift) LineCommand.DuplicateUp else LineCommand.MoveUp); true }
            alt && !ctrl && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && inEditor -> { lineCommand(if (shift) LineCommand.DuplicateDown else LineCommand.MoveDown); true }
            ctrl && (event.keyCode == KeyEvent.KEYCODE_EQUALS || event.keyCode == KeyEvent.KEYCODE_PLUS || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ADD) -> { zoom(1); true }
            ctrl && (event.keyCode == KeyEvent.KEYCODE_MINUS || event.keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) && !inTerminal -> { zoom(-1); true }
            ctrl && (event.keyCode == KeyEvent.KEYCODE_0 || event.keyCode == KeyEvent.KEYCODE_NUMPAD_0) && !inTerminal -> { zoom(0); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_S -> { if (shift) editorVm.saveAll() else editorVm.saveActive(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_F -> { controller.openSearch(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_W -> { activeKey?.let(editorVm::requestClose); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_TAB -> { if (shift) editorVm.previousTab() else editorVm.nextTab(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_GRAVE -> { toggleTerminal(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_B -> { toggleExplorer(); true }
            ctrl && event.keyCode == KeyEvent.KEYCODE_COMMA -> { onOpenSettings(); true }
            event.keyCode == KeyEvent.KEYCODE_F5 -> { editorVm.runActive(); true }
            else -> false
        }
    }
    DisposableEffect(activity) {
        activity?.shortcutHandler = { shortcutState.value(it) }
        onDispose { activity?.shortcutHandler = null }
    }

    // ---- Layout ---------------------------------------------------------------------------
    val explorerPane = @Composable { modifier: Modifier ->
        if (sideView == SideView.SourceControl) {
            SourceControlPane(
                state = sourceControl,
                viewModel = sourceControlVm,
                accountRequest = gitAccountRequest,
                // Commit and pull work on the files on disk, so unsaved editors are written first.
                onCommit = { message -> scope.launch { if (editorVm.saveAllNow()) sourceControlVm.commit(message) } },
                onPull = { scope.launch { if (editorVm.saveAllNow()) sourceControlVm.pull() } },
                onOpenChange = { change ->
                    (explorer.workspace?.root as? FileRef.Local)?.let { root ->
                        openFile(FileRef.Local(File(root.path, change.path).path), change.path.substringAfterLast('/'), change.path)
                    }
                },
                modifier = modifier,
            )
        } else if (sideView == SideView.Search) {
            SearchPane(
                state = search,
                viewModel = searchVm,
                focusRequest = searchFocusRequest,
                onOpenMatch = { file, match ->
                    editorVm.openAt(file.entry.ref, file.entry.name, file.relativePath, match.line, match.startColumn, match.endColumn)
                    closeDrawer()
                },
                modifier = modifier,
            )
        } else ExplorerPane(
            state = explorer,
            activeFileKey = activeKey,
            viewModel = explorerVm,
            onOpenFile = { row -> openFile(row.entry.ref, row.entry.name, row.relativePath) },
            onOpenFolder = { folderPicker.launch(null) },
            onNewProject = { newProject = true },
            onCloseFolder = explorerVm::closeWorkspace,
            modifier = modifier,
        )
    }

    // "Side Bar on the other side": the row (and the phone drawer) is laid out in the opposite
    // direction, and each part gets the real direction back for its own content.
    val direction = LocalLayoutDirection.current
    val sideDirection = if (settings.sidePosition == SidePosition.Start) direction else direction.opposite()
    val inOwnDirection = @Composable { content: @Composable () -> Unit ->
        CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
    }

    val workbench = @Composable {
        Column(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalLayoutDirection provides sideDirection) {
            Row(Modifier.weight(1f)) {
                inOwnDirection {
                ActivityBar(
                    explorerSelected = sideOpen && sideView == SideView.Explorer,
                    searchSelected = sideOpen && sideView == SideView.Search,
                    sourceControlSelected = sideOpen && sideView == SideView.SourceControl,
                    sourceControlBadge = sourceControl.git.changes.isNotEmpty(),
                    terminalSelected = panelVisible,
                    running = running,
                    onExplorer = { toggleExplorer() },
                    onSearch = { toggleSide(SideView.Search) },
                    onSourceControl = {
                        sourceControlVm.refresh()
                        toggleSide(SideView.SourceControl)
                    },
                    onRun = editorVm::runActive,
                    onTerminal = { toggleTerminal() },
                    onExtensions = onOpenExtensions,
                    onSettings = onOpenSettings,
                )
                }
                if (isTablet && sideBarVisible) {
                    Box(Modifier.width(280.dp).fillMaxHeight()) { inOwnDirection { explorerPane(Modifier.fillMaxSize()) } }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                inOwnDirection {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val maxPanel = (maxHeight.value * 0.8f).coerceAtLeast(120f)
                    val panelHeight = panelHeightDp.coerceIn(120f, maxPanel)
                    Column(Modifier.fillMaxSize()) {
                        EditorArea(
                            tabs = tabs,
                            activeKey = activeKey,
                            loading = loading,
                            settings = settings,
                            controller = controller,
                            viewModel = editorVm,
                            swipeToSwitchTabs = !isTablet,
                            actions = EditorActions(
                                onGoToLine = { quickPick = QuickPickMode.Line },
                                onGoToSymbol = { quickPick = QuickPickMode.Symbols },
                                onInsertSnippet = { quickPick = QuickPickMode.Snippets },
                                onCommandPalette = { quickPick = QuickPickMode.Commands },
                                onLineCommand = lineCommand,
                            ),
                            emptyContent = {
                                if (explorer.workspace == null) {
                                    WelcomePane(
                                        projects = projects,
                                        recent = recent,
                                        onOpenFolder = { folderPicker.launch(null) },
                                        onNewProject = { newProject = true },
                                        onOpen = explorerVm::openWorkspace,
                                        onForget = explorerVm::forget,
                                        onImportProjects = importProjects,
                                        onExportProject = { exportProjects(it.name) },
                                        onExportAll = { exportProjects(null) },
                                    )
                                } else {
                                    EmptyEditorHint()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (panelVisible) {
                            PanelResizeHandle { deltaDp -> panelHeightDp = (panelHeight - deltaDp).coerceIn(120f, maxPanel) }
                            TerminalPanel(
                                viewModel = terminalVm,
                                fontSizeSp = settings.terminalFontSizeSp,
                                showExtraKeys = settings.terminalExtraKeys,
                                onHide = { panelVisible = false },
                                onInstallRuntime = { runtimeSheet = false },
                                modifier = Modifier.fillMaxWidth().height(panelHeight.dp),
                            )
                        }
                    }
                }
                }
                }
            }
            }
            if (settings.showStatusBar) StatusBar(
                runtime = runtime,
                workspaceName = explorer.workspace?.name,
                document = activeTab?.let {
                    StatusDocumentInfo(cursor, it.document.language.displayName, it.document.lineEnding.label, settings.tabSize)
                },
                onRuntimeClick = { runtimeSheet = false },
                onPositionClick = { quickPick = QuickPickMode.Line },
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.activityBar)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.fillMaxSize().background(colors.editor)) {
            if (isTablet) {
                workbench()
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides sideDirection) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerState.isOpen,
                        drawerContent = {
                            ModalDrawerSheet(
                                drawerContainerColor = colors.sideBar,
                                drawerShape = RoundedCornerShape(0.dp),
                                modifier = Modifier.width(300.dp),
                            ) { inOwnDirection { explorerPane(Modifier.fillMaxSize()) } }
                        },
                    ) { inOwnDirection { workbench() } }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    // ---- Dialogs --------------------------------------------------------------------------
    runtimeSheet?.let { forRun -> RuntimeSheet(onDismiss = { runtimeSheet = null }, reasonIsRun = forRun) }
    if (newProject) {
        NewProjectDialog(
            onDismiss = { newProject = false },
            onCreate = { name, template ->
                newProject = false
                explorerVm.createProject(name, template) { ws ->
                    val main = template?.mainFile ?: return@createProject
                    val root = (ws.root as FileRef.Local).path
                    editorVm.open(FileRef.Local(File(root, main).path), main, main)
                }
            },
        )
    }
    confirmClose.firstOrNull()?.let { tab ->
        UnsavedChangesDialog(
            tab = tab,
            onSave = { confirmClose.remove(tab); editorVm.saveAndClose(tab.key) },
            onDiscard = { confirmClose.remove(tab); editorVm.closeDiscarding(tab.key) },
            // Cancel stops the whole "close several tabs" request, as in VS Code.
            onCancel = { confirmClose.clear() },
        )
    }
    quickPick?.let { mode ->
        val hasFile = activeTab != null
        val canComment = activeTab?.document?.language?.lineComment != null
        @Composable
        fun command(title: Int, shortcut: String? = null, enabled: Boolean = true, run: () -> Unit) =
            WorkbenchCommand(stringResource(title), shortcut, enabled, run)
        val commands = listOf(
            command(R.string.command_go_to_file, "Ctrl+P") { quickPick = QuickPickMode.Files },
            command(R.string.command_go_to_line, "Ctrl+G", hasFile) { quickPick = QuickPickMode.Line },
            command(R.string.command_go_to_symbol, "Ctrl+Shift+O", hasFile) { quickPick = QuickPickMode.Symbols },
            command(R.string.command_insert_snippet, enabled = hasFile) { quickPick = QuickPickMode.Snippets },
            command(R.string.command_extensions) { onOpenExtensions() },
            command(R.string.command_source_control) {
                sourceControlVm.refresh()
                if (!(sideOpen && sideView == SideView.SourceControl)) toggleSide(SideView.SourceControl)
            },
            command(R.string.command_find_in_files, "Ctrl+Shift+F") { findInFiles() },
            command(R.string.command_find, "Ctrl+F", hasFile) { controller.openSearch() },
            command(R.string.command_replace, "Ctrl+H", hasFile) { controller.openSearch(withReplace = true) },
            command(R.string.command_save, "Ctrl+S", activeTab?.dirty == true) { editorVm.saveActive() },
            command(R.string.command_save_all, "Ctrl+Shift+S", tabs.any { it.dirty }) { editorVm.saveAll() },
            command(R.string.command_run, "F5") { editorVm.runActive() },
            command(R.string.command_toggle_comment, "Ctrl+/", canComment) { lineCommand(LineCommand.ToggleComment) },
            command(R.string.command_duplicate_line, "Shift+Alt+↓", hasFile) { lineCommand(LineCommand.DuplicateDown) },
            command(R.string.command_move_line_up, "Alt+↑", hasFile) { lineCommand(LineCommand.MoveUp) },
            command(R.string.command_move_line_down, "Alt+↓", hasFile) { lineCommand(LineCommand.MoveDown) },
            command(R.string.command_delete_line, "Ctrl+Shift+K", hasFile) { lineCommand(LineCommand.Delete) },
            command(R.string.command_select_all, "Ctrl+A", hasFile) { controller.selectAll() },
            command(R.string.command_undo, "Ctrl+Z", hasFile) { controller.undo() },
            command(R.string.command_redo, "Ctrl+Y", hasFile) { controller.redo() },
            command(R.string.command_close_editor, "Ctrl+W", hasFile) { activeKey?.let(editorVm::requestClose) },
            command(R.string.command_close_other_editors, enabled = tabs.size > 1) { activeKey?.let(editorVm::closeOthers) },
            command(R.string.command_close_saved_editors, enabled = tabs.isNotEmpty()) { editorVm.closeSaved() },
            command(R.string.command_close_all_editors, enabled = tabs.isNotEmpty()) { editorVm.closeAll() },
            command(R.string.command_reopen_closed, "Ctrl+Shift+T") { editorVm.reopenClosed() },
            command(R.string.command_next_editor, "Ctrl+Tab", tabs.size > 1) { editorVm.nextTab() },
            command(R.string.command_previous_editor, "Ctrl+Shift+Tab", tabs.size > 1) { editorVm.previousTab() },
            command(R.string.command_toggle_explorer, "Ctrl+B") { toggleExplorer() },
            command(R.string.command_toggle_terminal, "Ctrl+`") { toggleTerminal() },
            command(R.string.command_new_terminal) { panelVisible = true; terminalVm.newShell() },
            command(R.string.command_toggle_word_wrap) { onUpdateSettings { it.copy(wordWrap = !it.wordWrap) } },
            command(R.string.command_toggle_line_numbers) { onUpdateSettings { it.copy(lineNumbers = !it.lineNumbers) } },
            command(R.string.command_toggle_whitespace) { onUpdateSettings { it.copy(showWhitespace = !it.showWhitespace) } },
            command(R.string.command_toggle_sticky_scroll) { onUpdateSettings { it.copy(stickyScroll = !it.stickyScroll) } },
            command(R.string.command_toggle_auto_save) {
                onUpdateSettings { it.copy(autoSave = if (it.autoSave == AutoSave.Off) AutoSave.AfterDelay else AutoSave.Off) }
            },
            command(R.string.command_toggle_theme) {
                // Choosing a theme by hand ends "follow the system theme".
                onUpdateSettings { it.copy(followSystemTheme = false, theme = if (colors.isDark) ThemeId.Light else ThemeId.NavyDark) }
            },
            command(R.string.command_toggle_side_position) {
                onUpdateSettings { it.copy(sidePosition = if (it.sidePosition == SidePosition.Start) SidePosition.End else SidePosition.Start) }
            },
            command(R.string.command_toggle_status_bar) { onUpdateSettings { it.copy(showStatusBar = !it.showStatusBar) } },
            command(R.string.command_zoom_in, "Ctrl+=") { zoom(1) },
            command(R.string.command_zoom_out, "Ctrl+-") { zoom(-1) },
            command(R.string.command_zoom_reset, "Ctrl+0") { zoom(0) },
            command(R.string.welcome_open_folder) { folderPicker.launch(null) },
            command(R.string.welcome_new_project) { newProject = true },
            command(R.string.explorer_close_folder, enabled = explorer.workspace != null) { explorerVm.closeWorkspace() },
            command(R.string.explorer_export_project, enabled = explorer.workspace?.kind == WorkspaceKind.Internal) {
                exportProjects(explorer.workspace?.name)
            },
            command(R.string.welcome_export_all, enabled = projects.isNotEmpty()) { exportProjects(null) },
            command(R.string.welcome_import_projects) { importProjects() },
            command(R.string.command_toolchain) { runtimeSheet = false },
            command(R.string.command_open_settings, "Ctrl+,") { onOpenSettings() },
        )
        // key: reopening in another mode (a command such as "Go to line") starts with that mode's prefix.
        androidx.compose.runtime.key(mode) {
            QuickPick(
                initialMode = mode,
                commands = commands,
                lineCount = if (hasFile) controller.lineCount else 0,
                loadSymbols = editorVm::symbolsOfActive,
                loadSnippets = editorVm::snippetsOfActive,
                onInsertSnippet = { snippet -> controller.insertSnippet(snippet, activeTab?.name.orEmpty()) },
                onOpenFile = { file -> openFile(file.entry.ref, file.entry.name, file.relativePath) },
                onGotoLine = { line, column -> controller.gotoLine(line, column) },
                onGotoSymbol = { symbol -> activeTab?.let { controller.reveal(it.document, symbol.line) } },
                onDismiss = { quickPick = null },
            )
        }
    }
}

private fun LayoutDirection.opposite() = if (this == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr

@Composable
private fun PanelResizeHandle(onDrag: (Float) -> Unit) {
    val colors = WorkbenchTheme
    val density = LocalDensity.current
    val label = stringResource(R.string.panel_resize)
    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(colors.tabBar)
            .semantics { contentDescription = label }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> onDrag(with(density) { delta.toDp().value }) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 36.dp, height = 4.dp).background(colors.border, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun EmptyEditorHint() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ZakomiLogo(size = 72.dp, contentDescription = null)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.editor_open_hint), color = WorkbenchTheme.textSecondary)
    }
}

@Composable
private fun UnsavedChangesDialog(tab: OpenTab, onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.editor_unsaved_title, tab.name)) },
        text = { Text(stringResource(R.string.editor_unsaved_message)) },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.dialog_save), color = WorkbenchTheme.accentText) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.dialog_dont_save), color = WorkbenchTheme.error) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) }
            }
        },
    )
}

/** Localized snackbar texts. Uses the composition's [Resources] so a language switch applies at once. */
private class EventTexts(private val res: Resources) {
    val runNothing: String get() = res.getString(R.string.run_nothing)
    val gitIdentityNeeded: String get() = res.getString(R.string.git_identity_needed)
    fun gitDone(operation: GitOperation, detail: String): String = when (operation) {
        GitOperation.Init -> res.getString(R.string.git_done_init)
        GitOperation.Commit -> res.getString(R.string.git_done_commit, detail)
        GitOperation.Push -> res.getString(R.string.git_done_push)
        GitOperation.Pull -> res.getString(R.string.git_done_pull)
        GitOperation.Clone -> res.getString(R.string.git_done_clone, detail)
        GitOperation.Remote -> res.getString(R.string.git_done_remote)
        GitOperation.Account -> res.getString(R.string.git_done_account)
    }
    fun gitFailed(kind: GitException.Kind, detail: String): String = when (kind) {
        GitException.Kind.Authentication -> res.getString(R.string.git_error_auth)
        GitException.Kind.Network -> res.getString(R.string.git_error_network)
        GitException.Kind.NoRemote -> res.getString(R.string.git_error_no_remote)
        GitException.Kind.NothingToCommit -> res.getString(R.string.git_error_nothing)
        GitException.Kind.Rejected -> res.getString(R.string.git_error_rejected)
        GitException.Kind.Conflict -> res.getString(R.string.git_error_conflict, detail)
        GitException.Kind.InvalidUrl -> res.getString(R.string.git_error_url)
        GitException.Kind.TargetExists -> res.getString(R.string.git_error_exists, detail)
        GitException.Kind.Other -> res.getString(R.string.git_error_other, detail)
    }
    val saveFolderFirst: String get() = res.getString(R.string.explorer_save_folder_first)
    val accessLost: String get() = res.getString(R.string.explorer_access_lost)
    fun runNotSupported(language: String) = res.getString(R.string.run_not_supported, language)
    fun fileMissing(name: String) = res.getString(R.string.editor_file_missing, name)
    fun saveFailed(name: String, detail: String) = res.getString(R.string.editor_save_failed, name, detail)
    fun runFailed(name: String, detail: String) = res.getString(R.string.run_failed, name, detail)
    fun explorerError(detail: String) = res.getString(R.string.explorer_error, detail)
    val nothingToExport: String get() = res.getString(R.string.projects_export_none)
    fun projectsExported(count: Int) = res.getQuantityString(R.plurals.projects_exported, count, count)
    fun exportFailed(detail: String) = res.getString(R.string.projects_export_failed, detail)
    fun projectsImported(names: List<String>) = res.getString(R.string.projects_imported, names.joinToString())
    fun importFailed(detail: String) = res.getString(R.string.projects_import_failed, detail)
    fun openFailed(name: String, reason: LoadResult.Reason, detail: String) = when (reason) {
        LoadResult.Reason.TooLarge -> res.getString(R.string.editor_open_too_large, name, (DocumentLoader.MAX_FILE_BYTES / (1024 * 1024)).toInt())
        LoadResult.Reason.Binary -> res.getString(R.string.editor_open_binary, name)
        LoadResult.Reason.NotUtf8 -> res.getString(R.string.editor_open_not_utf8, name)
        LoadResult.Reason.IoError -> res.getString(R.string.editor_open_failed, name, detail)
    }
}

@Composable
private fun rememberEventTexts(): EventTexts {
    val res = LocalResources.current
    return remember(res) { EventTexts(res) }
}
