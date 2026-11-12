package tech.zakomi.zakdroid.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.designsystem.ZakomiLogo

val ActivityBarWidth = 52.dp

/** VS Code-style Activity Bar: brand logo on top, views and actions below. */
@Composable
fun ActivityBar(
    explorerSelected: Boolean,
    searchSelected: Boolean,
    sourceControlSelected: Boolean,
    sourceControlBadge: Boolean,
    terminalSelected: Boolean,
    running: Boolean,
    onExplorer: () -> Unit,
    onSearch: () -> Unit,
    onSourceControl: () -> Unit,
    onRun: () -> Unit,
    onTerminal: () -> Unit,
    onExtensions: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    Column(
        modifier.width(ActivityBarWidth).fillMaxHeight().background(colors.activityBar),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(ActivityBarWidth), contentAlignment = Alignment.Center) {
            ZakomiLogo(size = 30.dp, contentDescription = stringResource(R.string.app_name))
        }
        ActivityItem(Icons.Outlined.Description, stringResource(R.string.action_explorer), explorerSelected, onExplorer)
        ActivityItem(Icons.Outlined.Search, stringResource(R.string.action_search), searchSelected, onSearch)
        ActivityItem(Icons.Outlined.AccountTree, stringResource(R.string.action_source_control), sourceControlSelected, onSourceControl, badge = sourceControlBadge)
        ActivityItem(Icons.Filled.PlayArrow, stringResource(R.string.action_run), false, onRun, busy = running)
        ActivityItem(Icons.Outlined.Terminal, stringResource(R.string.action_terminal), terminalSelected, onTerminal)
        ActivityItem(Icons.Outlined.Extension, stringResource(R.string.action_extensions), false, onExtensions)
        Spacer(Modifier.weight(1f))
        ActivityItem(Icons.Outlined.Settings, stringResource(R.string.action_settings), false, onSettings)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ActivityItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false,
    badge: Boolean = false,
) {
    val colors = WorkbenchTheme
    Box(
        modifier = Modifier
            .size(ActivityBarWidth)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(ActivityBarWidth)
                    .background(colors.activityBarIndicator),
            )
        }
        if (badge) {
            // Uncommitted changes: a dot in the same color as the icon, so it needs no new contrast pair.
            Box(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 10.dp).size(8.dp).background(colors.activityBarIconActive, CircleShape))
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), color = colors.activityBarIconActive, strokeWidth = 2.dp)
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.activityBarIconActive else colors.activityBarIcon,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
