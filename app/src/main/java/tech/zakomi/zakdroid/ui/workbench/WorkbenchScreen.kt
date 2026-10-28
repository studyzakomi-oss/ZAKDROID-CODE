package tech.zakomi.zakdroid.ui.workbench

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import tech.zakomi.zakdroid.core.settings.AppSettings
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

/**
 * The VS Code-style workbench. Tablets (>= 600dp) show the Side Bar next to the
 * editor; phones show one pane and open the Explorer as a drawer.
 */
@Composable
fun WorkbenchScreen(
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    explorerVm: ExplorerViewModel = hiltViewModel(),
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
    val recent by explorerVm.recent.collectAsStateWithLifecycle()
    val projects by explorerVm.projects.collectAsStateWithLifecycle()
    val tabs by editorVm.tabs.collectAsStateWithLifecycle()
    val activeKey by editorVm.activeKey.collectAsStateWithLifecycle()
    val loading by editorVm.loading.collectAsStateWithLifecycle()
    val running by editorVm.running.collectAsStateWithLifecycle()
    val runtime by runtimeVm.state.collectAsStateWithLifecycle()
    val cursor by controller.cursor.collectAsState()

    var sideBarVisible by rememberSaveable { mutableStateOf(true) }
    var panelVisible by rememberSaveable { mutableStateOf(false) }
    var panelHeightDp by rememberSaveable { mutableFloatStateOf(280f) }
    var runtimeSheet by rememberSaveable { mutableStateOf<Boolean?>(null) } // null = hidden, true = opened for a run
    var newProject by rememberSaveable { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf<OpenTab?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "folder"
            explorerVm.openSafFolder(uri, name)
        }
    }

    // ---- Events ---------------------------------------------------------------------------
    val messages = rememberEventTexts()
    LaunchedEffect(Unit) {
        editorVm.events.collect { event ->
            when (event) {
                is EditorEvent.Message -> snackbar.showSnackbar(event.text)
                is EditorEvent.ConfirmClose -> confirmClose = event.tab
                EditorEvent.RuntimeNotReady -> runtimeSheet = true
                EditorEvent.NothingToRun -> snackbar.showSnackbar(messages.runNothing)
                is EditorEvent.UnsupportedRun -> snackbar.showSnackbar(messages.runNotSupported(event.language.displayName))
                EditorEvent.RunStarted -> panelVisible = true
            }
        }
    }
    LaunchedEffect(Unit) {
        explorerVm.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    is ExplorerMessage.Error -> message.text
                    ExplorerMessage.SaveFolderFilesFirst -> messages.saveFolderFirst
                    ExplorerMessage.AccessLost -> messages.accessLost
                },
            )
        }
    }

    // ---- Actions --------------------------------------------------------------------------
    val toggleExplorer = {
        if (isTablet) sideBarVisible = !sideBarVisible else scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() }
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
        when {
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
        ExplorerPane(
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

    val workbench = @Composable {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                ActivityBar(
                    explorerSelected = if (isTablet) sideBarVisible else drawerState.isOpen,
                    terminalSelected = panelVisible,
                    running = running,
                    onExplorer = { toggleExplorer() },
                    onRun = editorVm::runActive,
                    onTerminal = { toggleTerminal() },
                    onSettings = onOpenSettings,
                )
                if (isTablet && sideBarVisible) {
                    explorerPane(Modifier.width(280.dp).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
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
                            emptyContent = {
                                if (explorer.workspace == null) {
                                    WelcomePane(
                                        projects = projects,
                                        recent = recent,
                                        onOpenFolder = { folderPicker.launch(null) },
                                        onNewProject = { newProject = true },
                                        onOpen = explorerVm::openWorkspace,
                                        onForget = explorerVm::forget,
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
                                onHide = { panelVisible = false },
                                onInstallRuntime = { runtimeSheet = false },
                                modifier = Modifier.fillMaxWidth().height(panelHeight.dp),
                            )
                        }
                    }
                }
            }
            val active = tabs.firstOrNull { it.key == activeKey }
            StatusBar(
                runtime = runtime,
                workspaceName = explorer.workspace?.name,
                document = active?.let {
                    StatusDocumentInfo(cursor, it.document.language.displayName, it.document.lineEnding.label)
                },
                onRuntimeClick = { runtimeSheet = false },
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
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerState.isOpen,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = colors.sideBar,
                            drawerShape = RoundedCornerShape(0.dp),
                            modifier = Modifier.width(300.dp),
                        ) { explorerPane(Modifier.fillMaxSize()) }
                    },
                ) { workbench() }
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
    confirmClose?.let { tab ->
        UnsavedChangesDialog(
            tab = tab,
            onSave = { confirmClose = null; editorVm.saveAndClose(tab.key) },
            onDiscard = { confirmClose = null; editorVm.closeDiscarding(tab.key) },
            onCancel = { confirmClose = null },
        )
    }
}

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

/** Localized snackbar texts, resolved once per composition. */
private class EventTexts(
    val runNothing: String,
    val saveFolderFirst: String,
    val accessLost: String,
    private val runNotSupportedFormat: String,
) {
    fun runNotSupported(language: String) = String.format(runNotSupportedFormat, language)
}

@Composable
private fun rememberEventTexts() = EventTexts(
    runNothing = stringResource(R.string.run_nothing),
    saveFolderFirst = stringResource(R.string.explorer_save_folder_first),
    accessLost = stringResource(R.string.explorer_access_lost),
    runNotSupportedFormat = stringResource(R.string.run_not_supported, "%1\$s"),
)
