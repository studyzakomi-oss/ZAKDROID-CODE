package tech.zakomi.zakdroid.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.editor.ui.CursorPosition
import tech.zakomi.zakdroid.runtime.RuntimeState
import tech.zakomi.zakdroid.ui.runtime.runtimeStatusLabel

data class StatusDocumentInfo(
    val cursor: CursorPosition,
    val language: String,
    val lineEnding: String,
)

@Composable
fun StatusBar(
    runtime: RuntimeState,
    workspaceName: String?,
    document: StatusDocumentInfo?,
    onRuntimeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    val runtimeLabel = runtimeStatusLabel(runtime)
    val runtimeDescription = stringResource(R.string.runtime_status_content_description, runtimeLabel)
    Row(
        modifier.fillMaxWidth().height(26.dp).background(colors.statusBar).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .clickable(onClick = onRuntimeClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = runtimeDescription
                    role = Role.Button
                }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(runtimeDotColor(runtime), CircleShape))
            Spacer(Modifier.width(6.dp))
            StatusText(runtimeLabel)
        }
        StatusText(workspaceName ?: stringResource(R.string.status_no_folder), Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        if (document != null) {
            StatusText(stringResource(R.string.status_line_col, document.cursor.line, document.cursor.column))
            StatusText("UTF-8")
            StatusText(document.lineEnding)
            StatusText(document.language)
        }
    }
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = WorkbenchTheme.statusBarText,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 6.dp),
    )
}

/** Status dots sit on the navy bar, so they use the dark-theme status colors in both themes. */
private fun runtimeDotColor(state: RuntimeState): Color = when (state) {
    RuntimeState.Ready -> Color(0xFF3FB950)
    is RuntimeState.Installing, RuntimeState.Stopping -> Color(0xFFE3B341)
    is RuntimeState.Error -> Color(0xFFFF7B72)
    is RuntimeState.NotInstalled -> Color(0xFFA9B8DE)
}
