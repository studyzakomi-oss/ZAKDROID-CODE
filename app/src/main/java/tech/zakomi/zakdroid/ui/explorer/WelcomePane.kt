package tech.zakomi.zakdroid.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.BrandCapsStyle
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.designsystem.ZakomiLogo
import tech.zakomi.zakdroid.core.workspace.Workspace
import tech.zakomi.zakdroid.core.workspace.WorkspaceKind

/** Shown in the editor area when no folder is open, like VS Code's Welcome page. */
@Composable
fun WelcomePane(
    projects: List<Workspace>,
    recent: List<Workspace>,
    onOpenFolder: () -> Unit,
    onNewProject: () -> Unit,
    onOpen: (Workspace) -> Unit,
    onForget: (Workspace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    Column(
        modifier.fillMaxSize().background(colors.editor).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZakomiLogo(size = 56.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    Text(stringResource(R.string.brand_by).uppercase(), style = BrandCapsStyle, color = colors.accentText)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.welcome_subtitle), color = colors.textSecondary)
            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.welcome_start))
            WelcomeLink(Icons.Outlined.CreateNewFolder, stringResource(R.string.welcome_new_project), onNewProject)
            WelcomeLink(Icons.Outlined.FolderOpen, stringResource(R.string.welcome_open_folder), onOpenFolder)
            if (projects.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionTitle(stringResource(R.string.welcome_projects))
                projects.forEach { WorkspaceRow(it, onOpen, onForget = null) }
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle(stringResource(R.string.welcome_recent))
            if (recent.isEmpty()) {
                Text(stringResource(R.string.welcome_no_recent), color = colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                recent.forEach { WorkspaceRow(it, onOpen, onForget) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = WorkbenchTheme.textPrimary, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun WelcomeLink(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = WorkbenchTheme
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentText)
        Text(label, color = colors.accentText, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WorkspaceRow(workspace: Workspace, onOpen: (Workspace) -> Unit, onForget: ((Workspace) -> Unit)?) {
    val colors = WorkbenchTheme
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable { onOpen(workspace) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (workspace.kind == WorkspaceKind.Internal) Icons.Outlined.PhoneAndroid else Icons.Outlined.Folder,
            contentDescription = null,
            tint = colors.textSecondary,
        )
        Text(workspace.name, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onForget != null) {
            IconButton(onClick = { onForget(workspace) }) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.explorer_forget), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
