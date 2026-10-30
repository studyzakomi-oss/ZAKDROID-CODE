package tech.zakomi.zakdroid.ui.runtime

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.JetBrainsMono
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.runtime.InstallStep
import tech.zakomi.zakdroid.runtime.RuntimeErrorKind
import tech.zakomi.zakdroid.runtime.RuntimeState
import tech.zakomi.zakdroid.runtime.labelRes
import tech.zakomi.zakdroid.ui.common.BackgroundActivityCard

/** Short state label: Not installed / Installing x% / Ready / Error. */
@Composable
fun runtimeStatusLabel(state: RuntimeState): String = when (state) {
    is RuntimeState.NotInstalled -> stringResource(R.string.runtime_not_installed)
    is RuntimeState.Installing -> stringResource(R.string.runtime_installing, state.percent)
    RuntimeState.Stopping -> stringResource(R.string.runtime_stopping)
    RuntimeState.Ready -> stringResource(R.string.runtime_ready)
    is RuntimeState.Error -> stringResource(R.string.runtime_error)
}

/** Returns a callback that asks for notification permission (Android 13+) and then runs [action]. */
@Composable
fun rememberWithNotificationPermission(action: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { action() }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else action()
    }
}

@Composable
fun RuntimeSheet(
    onDismiss: () -> Unit,
    reasonIsRun: Boolean = false,
    viewModel: RuntimeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = WorkbenchTheme
    val install = rememberWithNotificationPermission(viewModel::install)
    val repair = rememberWithNotificationPermission(viewModel::repair)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.runtime_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (reasonIsRun && state != RuntimeState.Ready) {
                    Text(stringResource(R.string.runtime_needed_for_run), fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                }
                Text(viewModel.description, style = MaterialTheme.typography.labelMedium, color = colors.accentText)
                Spacer(Modifier.height(8.dp))
                when (val s = state) {
                    is RuntimeState.NotInstalled -> {
                        Text(stringResource(R.string.runtime_intro), color = colors.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.runtime_space_note), color = colors.textSecondary)
                        BackgroundActivityCard(onlyWhenRestricted = true, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.runtime_packages, viewModel.packages.joinToString(", ")), color = colors.textSecondary)
                    }
                    is RuntimeState.Installing -> {
                        InstallProgress(s)
                        BackgroundActivityCard(onlyWhenRestricted = true, modifier = Modifier.padding(top = 8.dp))
                    }
                    RuntimeState.Stopping -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accentText)
                        Text(stringResource(R.string.runtime_stopping_message), color = colors.textPrimary)
                    }
                    RuntimeState.Ready -> {
                        Text(stringResource(R.string.runtime_ready), color = colors.success, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.runtime_packages, viewModel.packages.joinToString(", ")), color = colors.textSecondary)
                    }
                    is RuntimeState.Error -> {
                        val locked = s.kind == RuntimeErrorKind.Locked
                        Text(
                            stringResource(if (locked) R.string.runtime_locked_title else R.string.runtime_error_title),
                            color = colors.error,
                            fontWeight = FontWeight.Bold,
                        )
                        if (locked) {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.runtime_locked_message), color = colors.textPrimary)
                        }
                        s.step?.let {
                            Text(stringResource(it.labelRes), color = colors.textSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.message,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .background(colors.editor, RoundedCornerShape(6.dp))
                                .horizontalScroll(rememberScrollState())
                                .padding(8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is RuntimeState.NotInstalled -> TextButton(onClick = install) {
                    Text(stringResource(if (s.canResume) R.string.runtime_resume else R.string.runtime_install))
                }
                is RuntimeState.Installing -> TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.runtime_cancel), color = colors.error) }
                RuntimeState.Stopping -> TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.runtime_stopping)) }
                is RuntimeState.Error -> Row {
                    if (s.kind == RuntimeErrorKind.Locked) {
                        TextButton(onClick = repair) { Text(stringResource(R.string.runtime_repair)) }
                    }
                    TextButton(onClick = install) { Text(stringResource(R.string.runtime_retry)) }
                }
                RuntimeState.Ready -> Unit
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@Composable
private fun InstallProgress(state: RuntimeState.Installing) {
    val colors = WorkbenchTheme
    LinearProgressIndicator(
        progress = { state.percent / 100f },
        modifier = Modifier.fillMaxWidth(),
        color = colors.accentText,
        trackColor = colors.border,
    )
    Spacer(Modifier.height(4.dp))
    Text("${state.percent}%", color = colors.textSecondary, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    InstallStep.entries.forEach { step ->
        val done = step.ordinal < state.step.ordinal
        val current = step == state.step
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
            when {
                done -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                current -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accentText)
                else -> Icon(Icons.Outlined.Circle, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
            Text(
                stringResource(step.labelRes),
                color = if (current) colors.textPrimary else colors.textSecondary,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        if (current && state.detail != null) {
            Text(
                state.detail!!,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                modifier = Modifier.padding(start = 26.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.runtime_background_note), color = colors.textSecondary, fontSize = 12.sp)
    Spacer(Modifier.width(1.dp))
}
