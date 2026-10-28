package tech.zakomi.zakdroid.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Description
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
    terminalSelected: Boolean,
    running: Boolean,
    onExplorer: () -> Unit,
    onRun: () -> Unit,
    onTerminal: () -> Unit,
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
        ActivityItem(Icons.Filled.PlayArrow, stringResource(R.string.action_run), false, onRun, busy = running)
        ActivityItem(Icons.Outlined.Terminal, stringResource(R.string.action_terminal), terminalSelected, onTerminal)
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
