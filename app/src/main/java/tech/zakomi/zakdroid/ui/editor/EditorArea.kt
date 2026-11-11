package tech.zakomi.zakdroid.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.settings.AppSettings
import tech.zakomi.zakdroid.editor.ui.CodeEditorPane
import tech.zakomi.zakdroid.editor.ui.EditorController
import tech.zakomi.zakdroid.editor.ui.EditorViewSettings
import tech.zakomi.zakdroid.editor.ui.FindReplaceBar
import tech.zakomi.zakdroid.editor.ui.LineCommand
import tech.zakomi.zakdroid.state.OpenTab

@Composable
fun EditorArea(
    tabs: List<OpenTab>,
    activeKey: String?,
    loading: Boolean,
    settings: AppSettings,
    controller: EditorController,
    viewModel: EditorViewModel,
    swipeToSwitchTabs: Boolean,
    actions: EditorActions,
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    val active = tabs.firstOrNull { it.key == activeKey }
    val search by controller.search.collectAsState()
    Column(modifier.background(colors.editor)) {
        if (tabs.isNotEmpty()) {
            TabStrip(tabs, activeKey, viewModel)
        }
        if (active != null) {
            EditorToolbar(
                tab = active,
                controller = controller,
                onSave = viewModel::saveActive,
                onFind = { controller.openSearch() },
                actions = actions,
                onSwipe = if (swipeToSwitchTabs && tabs.size > 1) { forward -> if (forward) viewModel.nextTab() else viewModel.previousTab() } else null,
            )
            if (search.active) FindReplaceBar(controller)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (active != null) {
                CodeEditorPane(
                    document = active.document,
                    settings = EditorViewSettings(
                        fontSizeSp = settings.editorFontSizeSp,
                        ligatures = settings.ligatures,
                        wordWrap = settings.wordWrap,
                        tabSize = settings.tabSize,
                        lineNumbers = settings.lineNumbers,
                        showWhitespace = settings.showWhitespace,
                        stickyScroll = settings.stickyScroll,
                        lineHeightPercent = settings.editorLineHeightPercent,
                    ),
                    controller = controller,
                    languageFactory = viewModel.textMate::newLanguage,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                emptyContent()
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = colors.accentText, trackColor = Color.Transparent)
            }
        }
    }
}

/** Workbench actions offered by the editor's overflow menu (touch access to keyboard commands). */
class EditorActions(
    val onGoToLine: () -> Unit,
    val onGoToSymbol: () -> Unit,
    val onInsertSnippet: () -> Unit,
    val onCommandPalette: () -> Unit,
    val onLineCommand: (LineCommand) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabStrip(tabs: List<OpenTab>, activeKey: String?, viewModel: EditorViewModel) {
    val onSelect = viewModel::activate
    val onClose = viewModel::requestClose
    val colors = WorkbenchTheme
    @Suppress("DEPRECATION") // LocalClipboard needs a suspend call; plain text copy does not.
    val clipboard = LocalClipboardManager.current
    var menuFor by remember { mutableStateOf<String?>(null) }
    val moreLabel = stringResource(R.string.tab_more_actions)
    val listState = rememberLazyListState()
    val activeIndex = tabs.indexOfFirst { it.key == activeKey }
    LaunchedEffect(activeIndex) { if (activeIndex >= 0) listState.animateScrollToItem(activeIndex) }
    LazyRow(state = listState, modifier = Modifier.fillMaxWidth().height(40.dp).background(colors.tabBar)) {
        itemsIndexed(tabs, key = { _, tab -> tab.key }) { _, tab ->
            val isActive = tab.key == activeKey
            Box(
                Modifier
                    .fillMaxHeight()
                    .background(if (isActive) colors.tabActive else colors.tabInactive)
                    .combinedClickable(onClick = { onSelect(tab.key) }, onLongClick = { menuFor = tab.key })
                    .semantics {
                        role = Role.Tab
                        selected = isActive
                        customActions = listOf(CustomAccessibilityAction(moreLabel) { menuFor = tab.key; true })
                    },
            ) {
                DropdownMenu(expanded = menuFor == tab.key, onDismissRequest = { menuFor = null }) {
                    @Composable
                    fun item(label: Int, enabled: Boolean = true, action: () -> Unit) = DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        enabled = enabled,
                        onClick = { menuFor = null; action() },
                    )
                    item(R.string.tab_close) { onClose(tab.key) }
                    item(R.string.tab_close_others, enabled = tabs.size > 1) { viewModel.closeOthers(tab.key) }
                    item(R.string.tab_close_right, enabled = tabs.lastOrNull()?.key != tab.key) { viewModel.closeToTheRight(tab.key) }
                    item(R.string.tab_close_saved) { viewModel.closeSaved() }
                    item(R.string.tab_close_all) { viewModel.closeAll() }
                    HorizontalDivider()
                    item(R.string.tab_copy_path) { clipboard.setText(AnnotatedString(tab.relativePath)) }
                }
                if (isActive) Box(Modifier.fillMaxWidth().height(2.dp).background(colors.tabActiveIndicator).align(Alignment.TopStart))
                Row(Modifier.fillMaxHeight().padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tab.name,
                        color = if (isActive) colors.tabActiveText else colors.tabInactiveText,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                    val closeLabel = stringResource(R.string.editor_close_tab, tab.name)
                    val unsaved = stringResource(R.string.editor_unsaved)
                    Box(
                        Modifier
                            .size(36.dp)
                            .clickable { onClose(tab.key) }
                            .semantics { contentDescription = if (tab.dirty) "$unsaved. $closeLabel" else closeLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tab.dirty) {
                            Box(Modifier.size(9.dp).background(if (isActive) colors.tabActiveText else colors.tabInactiveText, CircleShape))
                        } else {
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = colors.tabInactiveText, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
        }
    }
}

@Composable
private fun EditorToolbar(
    tab: OpenTab,
    controller: EditorController,
    onSave: () -> Unit,
    onFind: () -> Unit,
    actions: EditorActions,
    onSwipe: ((forward: Boolean) -> Unit)?,
) {
    val colors = WorkbenchTheme
    val canUndo by controller.canUndo.collectAsState()
    val canRedo by controller.canRedo.collectAsState()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(colors.editor).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val swipeHint = stringResource(R.string.editor_swipe_hint)
        Text(
            tab.relativePath.replace("/", " › "),
            color = colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onSwipe == null) Modifier else Modifier
                        .semantics { contentDescription = "${tab.relativePath}. $swipeHint" }
                        .pointerInput(onSwipe, rtl) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (kotlin.math.abs(total) > 80f) onSwipe((total < 0) != rtl)
                                },
                            ) { _, amount -> total += amount }
                        },
                ),
        )
        ToolbarButton(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.editor_undo), canUndo, controller::undo)
        ToolbarButton(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.editor_redo), canRedo, controller::redo)
        ToolbarButton(Icons.Outlined.Search, stringResource(R.string.editor_find), true, onFind)
        ToolbarButton(Icons.Outlined.Save, stringResource(R.string.editor_save), tab.dirty, onSave)
        var menu by remember { mutableStateOf(false) }
        Box {
            ToolbarButton(Icons.Outlined.MoreVert, stringResource(R.string.editor_more_actions), true) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                @Composable
                fun item(label: Int, enabled: Boolean = true, action: () -> Unit) = DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    enabled = enabled,
                    onClick = { menu = false; action() },
                )
                val canComment = tab.document.language.lineComment != null
                item(R.string.command_palette, action = actions.onCommandPalette)
                item(R.string.command_go_to_line, action = actions.onGoToLine)
                item(R.string.command_go_to_symbol, action = actions.onGoToSymbol)
                item(R.string.command_insert_snippet, enabled = tab.document.language.id != null, action = actions.onInsertSnippet)
                HorizontalDivider()
                item(R.string.command_toggle_comment, enabled = canComment) { actions.onLineCommand(LineCommand.ToggleComment) }
                item(R.string.command_duplicate_line) { actions.onLineCommand(LineCommand.DuplicateDown) }
                item(R.string.command_move_line_up) { actions.onLineCommand(LineCommand.MoveUp) }
                item(R.string.command_move_line_down) { actions.onLineCommand(LineCommand.MoveDown) }
                item(R.string.command_delete_line) { actions.onLineCommand(LineCommand.Delete) }
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun ToolbarButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = WorkbenchTheme
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, tint = if (enabled) colors.textPrimary else colors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}
