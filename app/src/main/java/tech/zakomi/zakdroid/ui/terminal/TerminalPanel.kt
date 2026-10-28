package tech.zakomi.zakdroid.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.runtime.RuntimeState
import tech.zakomi.zakdroid.terminal.ExtraKeysRow
import tech.zakomi.zakdroid.terminal.ShellSpec
import tech.zakomi.zakdroid.terminal.TerminalPane

/** The bottom Panel with terminal tabs, the terminal itself and the touch key row. */
@Composable
fun TerminalPanel(
    viewModel: TerminalViewModel,
    fontSizeSp: Int,
    onHide: () -> Unit,
    onInstallRuntime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val runtime by viewModel.runtimeState.collectAsStateWithLifecycle()

    Column(modifier.background(colors.panel)) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).background(colors.tabBar).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.terminal_title), style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
            LazyRow(Modifier.weight(1f).padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items(tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeId
                    Row(
                        Modifier
                            .fillMaxHeight()
                            .background(if (isActive) colors.panel else colors.tabBar)
                            .clickable { viewModel.select(tab.id) }
                            .semantics {
                                role = Role.Tab
                                selected = isActive
                            }
                            .padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val prefix = if (tab.kind == ShellSpec.Kind.Run) "▶ " else ""
                        Text(prefix + tab.title, color = if (isActive) colors.tabActiveText else colors.tabInactiveText, fontSize = 12.sp, maxLines = 1)
                        tab.exitCode?.takeIf { !tab.running }?.let { code ->
                            Text(
                                " · " + stringResource(R.string.terminal_exited, code),
                                color = if (code == 0) colors.success else colors.error,
                                fontSize = 12.sp,
                            )
                        }
                        IconButton(onClick = { viewModel.close(tab.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.terminal_close, tab.title), tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            IconButton(onClick = { if (!viewModel.newShell()) onInstallRuntime() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Add, stringResource(R.string.terminal_new), tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onHide, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.panel_hide), tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            }
        }
        val id = activeId
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (id != null) {
                TerminalPane(
                    manager = viewModel.manager,
                    sessionId = id,
                    fontSizeSp = fontSizeSp,
                    keys = viewModel.keys,
                    onFontScale = viewModel::zoom,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (runtime == RuntimeState.Ready) {
                        Text(stringResource(R.string.terminal_empty), color = colors.textSecondary)
                        TextButton(onClick = { viewModel.newShell() }) { Text(stringResource(R.string.terminal_new), color = colors.accentText) }
                    } else {
                        Text(stringResource(R.string.terminal_needs_runtime), color = colors.textSecondary)
                        TextButton(onClick = onInstallRuntime) { Text(stringResource(R.string.runtime_title), color = colors.accentText) }
                    }
                }
            }
        }
        if (id != null) ExtraKeysRow(viewModel.keys)
        Spacer(Modifier.height(0.dp))
    }
}
