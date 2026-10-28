package tech.zakomi.zakdroid.ui.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.editor.EditorLanguage
import tech.zakomi.zakdroid.ui.common.ConfirmDialog
import tech.zakomi.zakdroid.ui.common.NameDialog

private sealed interface ExplorerDialog {
    data class NewFile(val parent: FileRef, val parentRelative: String) : ExplorerDialog
    data class NewFolder(val parent: FileRef) : ExplorerDialog
    data class Rename(val row: TreeRow) : ExplorerDialog
    data class Delete(val row: TreeRow) : ExplorerDialog
}

/**
 * The Explorer side bar: file tree with lazy folder loading, name search,
 * and long-press actions (new, rename, move, delete).
 */
@Composable
fun ExplorerPane(
    state: ExplorerUiState,
    activeFileKey: String?,
    viewModel: ExplorerViewModel,
    onOpenFile: (TreeRow) -> Unit,
    onOpenFolder: () -> Unit,
    onNewProject: () -> Unit,
    onCloseFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    var dialog by remember { mutableStateOf<ExplorerDialog?>(null) }
    val workspace = state.workspace

    Column(modifier.fillMaxSize().background(colors.sideBar)) {
        // Header
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                workspace?.name?.uppercase() ?: stringResource(R.string.explorer_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (workspace != null) {
                HeaderAction(Icons.AutoMirrored.Outlined.NoteAdd, stringResource(R.string.explorer_new_file)) { dialog = ExplorerDialog.NewFile(workspace.root, "") }
                HeaderAction(Icons.Outlined.CreateNewFolder, stringResource(R.string.explorer_new_folder)) { dialog = ExplorerDialog.NewFolder(workspace.root) }
                HeaderAction(Icons.Outlined.Refresh, stringResource(R.string.explorer_refresh)) { viewModel.reload() }
                var menu by remember { mutableStateOf(false) }
                Box {
                    HeaderAction(Icons.Outlined.MoreVert, stringResource(R.string.explorer_more)) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.welcome_open_folder)) }, onClick = { menu = false; onOpenFolder() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.welcome_new_project)) }, onClick = { menu = false; onNewProject() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.explorer_close_folder)) }, onClick = { menu = false; onCloseFolder() })
                    }
                }
            }
        }
        if (workspace == null) {
            Column(Modifier.padding(12.dp)) {
                TextButton(onClick = onOpenFolder) { Text(stringResource(R.string.welcome_open_folder), color = colors.accentText) }
                TextButton(onClick = onNewProject) { Text(stringResource(R.string.welcome_new_project), color = colors.accentText) }
            }
            return@Column
        }

        SearchBox(state.query, viewModel::search)
        if (state.loading || state.searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = colors.accentText, trackColor = Color.Transparent)
        }
        state.cut?.let { cut ->
            Column(Modifier.fillMaxWidth().background(colors.selection).padding(8.dp)) {
                Text(stringResource(R.string.explorer_moving, cut.entry.name), color = colors.selectionText, fontSize = 13.sp)
                Row {
                    TextButton(onClick = { viewModel.pasteInto(null) }) { Text(stringResource(R.string.explorer_paste_root), color = colors.selectionText) }
                    TextButton(onClick = viewModel::cancelCut) { Text(stringResource(R.string.explorer_cancel_cut), color = colors.selectionText) }
                }
            }
        }

        val rows = state.results ?: state.rows
        if (rows.isEmpty() && !state.loading && !state.searching) {
            Text(
                stringResource(if (state.results != null) R.string.explorer_no_results else R.string.explorer_empty),
                color = colors.textSecondary,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.entry.ref.key }) { row ->
                TreeItem(
                    row = row,
                    showPath = state.results != null,
                    active = row.entry.ref.key == activeFileKey,
                    cutActive = state.cut != null,
                    onClick = { if (row.entry.isDirectory) viewModel.toggle(row) else onOpenFile(row) },
                    onNewFile = { dialog = ExplorerDialog.NewFile(row.entry.ref, row.relativePath) },
                    onNewFolder = { dialog = ExplorerDialog.NewFolder(row.entry.ref) },
                    onRename = { dialog = ExplorerDialog.Rename(row) },
                    onDelete = { dialog = ExplorerDialog.Delete(row) },
                    onCut = { viewModel.cut(row) },
                    onPasteHere = { viewModel.pasteInto(row) },
                )
            }
        }
    }

    when (val d = dialog) {
        is ExplorerDialog.NewFile -> NameDialog(
            title = stringResource(R.string.explorer_new_file),
            confirmLabel = stringResource(R.string.dialog_create),
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                viewModel.createFile(d.parent, d.parentRelative, name) { onOpenFile(it) }
            },
        )
        is ExplorerDialog.NewFolder -> NameDialog(
            title = stringResource(R.string.explorer_new_folder),
            confirmLabel = stringResource(R.string.dialog_create),
            onDismiss = { dialog = null },
            onConfirm = { name -> dialog = null; viewModel.createFolder(d.parent, name) },
        )
        is ExplorerDialog.Rename -> NameDialog(
            title = stringResource(R.string.explorer_rename),
            confirmLabel = stringResource(R.string.dialog_rename),
            initial = d.row.entry.name,
            onDismiss = { dialog = null },
            onConfirm = { name -> dialog = null; viewModel.rename(d.row, name) },
        )
        is ExplorerDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.explorer_delete_title, d.row.entry.name),
            message = stringResource(if (d.row.entry.isDirectory) R.string.explorer_delete_folder_message else R.string.explorer_delete_file_message),
            confirmLabel = stringResource(R.string.dialog_delete),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; viewModel.delete(d.row) },
        )
        null -> Unit
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, tint = WorkbenchTheme.textSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val colors = WorkbenchTheme
    val hint = stringResource(R.string.explorer_search_hint)
    Row(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(36.dp)
            .background(colors.editor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(hint, color = colors.textSecondary, fontSize = 14.sp, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accentText),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeItem(
    row: TreeRow,
    showPath: Boolean,
    active: Boolean,
    cutActive: Boolean,
    onClick: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCut: () -> Unit,
    onPasteHere: () -> Unit,
) {
    val colors = WorkbenchTheme
    var menu by remember { mutableStateOf(false) }
    val entry = row.entry
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(if (active) colors.selection else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(start = (8 + row.depth * 14).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.isDirectory) {
                Icon(
                    if (row.expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Spacer(Modifier.width(18.dp))
            }
            Icon(
                if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                contentDescription = null,
                tint = fileTint(entry.name, entry.isDirectory),
                modifier = Modifier.padding(horizontal = 4.dp).size(18.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    color = if (active) colors.selectionText else colors.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showPath) {
                    Text(
                        row.relativePath,
                        color = if (active) colors.selectionText else colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (entry.isDirectory) {
                if (cutActive) DropdownMenuItem(text = { Text(stringResource(R.string.explorer_paste_here)) }, onClick = { menu = false; onPasteHere() })
                DropdownMenuItem(text = { Text(stringResource(R.string.explorer_new_file)) }, onClick = { menu = false; onNewFile() })
                DropdownMenuItem(text = { Text(stringResource(R.string.explorer_new_folder)) }, onClick = { menu = false; onNewFolder() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.explorer_rename)) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.explorer_cut)) }, onClick = { menu = false; onCut() })
            DropdownMenuItem(text = { Text(stringResource(R.string.explorer_delete), color = colors.error) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun fileTint(name: String, isDirectory: Boolean): Color {
    val colors = WorkbenchTheme
    if (isDirectory) return colors.accentText
    return when (EditorLanguage.fromFileName(name)) {
        EditorLanguage.C, EditorLanguage.Cpp -> colors.accentText
        EditorLanguage.Python -> colors.warning
        EditorLanguage.PlainText -> colors.textSecondary
    }
}
