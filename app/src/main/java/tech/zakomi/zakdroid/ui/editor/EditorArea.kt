package tech.zakomi.zakdroid.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    val active = tabs.firstOrNull { it.key == activeKey }
    val search by controller.search.collectAsState()
    Column(modifier.background(colors.editor)) {
        if (tabs.isNotEmpty()) {
            TabStrip(tabs, activeKey, onSelect = viewModel::activate, onClose = viewModel::requestClose)
        }
        if (active != null) {
            EditorToolbar(
                tab = active,
                controller = controller,
                onSave = viewModel::saveActive,
                onFind = controller::openSearch,
                onSwipe = if (swipeToSwitchTabs && tabs.size > 1) { forward -> if (forward) viewModel.nextTab() else viewModel.previousTab() } else null,
            )
            if (search.active) FindReplaceBar(controller)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (active != null) {
                CodeEditorPane(
                    document = active.document,
                    settings = EditorViewSettings(settings.editorFontSizeSp, settings.ligatures, settings.wordWrap, settings.tabSize),
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

@Composable
private fun TabStrip(tabs: List<OpenTab>, activeKey: String?, onSelect: (String) -> Unit, onClose: (String) -> Unit) {
    val colors = WorkbenchTheme
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
                    .clickable { onSelect(tab.key) }
                    .semantics {
                        role = Role.Tab
                        selected = isActive
                    },
            ) {
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
