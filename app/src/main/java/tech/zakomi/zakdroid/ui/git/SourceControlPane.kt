package tech.zakomi.zakdroid.ui.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.JetBrainsMono
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.git.ChangeKind
import tech.zakomi.zakdroid.git.GitChange

private enum class GitDialog { Account, Remote, Clone }

/** The Source Control view of the Side Bar: changes, commit, push and pull for the open project. */
@Composable
fun SourceControlPane(
    state: SourceControlUiState,
    viewModel: SourceControlViewModel,
    /** Changes whenever the account dialog should open (the view model asked for an identity). */
    accountRequest: Int,
    onCommit: (String) -> Unit,
    onPull: () -> Unit,
    onOpenChange: (GitChange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    var dialog by remember { mutableStateOf<GitDialog?>(null) }
    var message by rememberSaveable { mutableStateOf("") }
    val git = state.git
    LaunchedEffect(accountRequest) { if (accountRequest > 0) dialog = GitDialog.Account }
    LaunchedEffect(git.log.firstOrNull()?.shortId) { message = "" } // a new commit clears the box

    Column(modifier.fillMaxSize().background(colors.sideBar)) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.git_title), style = MaterialTheme.typography.labelMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { dialog = GitDialog.Account }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Person, stringResource(R.string.git_account), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
            if (git.isRepository) {
                IconButton(onClick = viewModel::refresh, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.explorer_refresh), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = colors.accentText, trackColor = Color.Transparent)
        } else {
            Spacer(Modifier.height(2.dp))
        }

        if (!git.isRepository) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val text = when {
                    !state.hasWorkspace -> R.string.git_no_folder
                    !state.supported -> R.string.git_not_supported
                    else -> R.string.git_not_a_repository
                }
                Text(stringResource(text), color = colors.textSecondary, fontSize = 13.sp)
                if (state.hasWorkspace && state.supported) {
                    OutlinedButton(onClick = viewModel::initialize, enabled = !state.busy) { Text(stringResource(R.string.git_init)) }
                }
                OutlinedButton(onClick = { dialog = GitDialog.Clone }, enabled = !state.busy) { Text(stringResource(R.string.git_clone)) }
            }
            if (dialog != null) Dialogs(dialog, state, viewModel) { dialog = null }
            return@Column
        }

        // Branch and remote
        Column(Modifier.padding(horizontal = 12.dp)) {
            val sync = if (git.ahead != null && git.behind != null) "  ↑${git.ahead} ↓${git.behind}" else ""
            Text(stringResource(R.string.git_branch, git.branch) + sync, color = colors.textPrimary, fontSize = 13.sp)
            Text(
                git.remoteUrl ?: stringResource(R.string.git_no_remote),
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
            if (!state.hasToken && git.remoteUrl?.startsWith("https://") == true) {
                Text(stringResource(R.string.git_token_missing), color = colors.warning, fontSize = 12.sp)
            }
        }

        // Commit box
        val hint = stringResource(R.string.git_message_hint)
        val shape = RoundedCornerShape(4.dp)
        BasicTextField(
            value = message,
            onValueChange = { message = it },
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.accentText),
            maxLines = 4,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .background(colors.editor, shape)
                .border(1.dp, colors.border, shape)
                .padding(8.dp)
                .semantics { contentDescription = hint },
            decorationBox = { inner ->
                if (message.isEmpty()) Text(hint, color = colors.textSecondary, fontSize = 14.sp)
                inner()
            },
        )
        FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onCommit(message) }, enabled = !state.busy && message.isNotBlank() && git.changes.isNotEmpty()) {
                Text(stringResource(R.string.git_commit))
            }
            OutlinedButton(onClick = viewModel::push, enabled = !state.busy && git.remoteUrl != null && git.log.isNotEmpty()) {
                Text(stringResource(R.string.git_push))
            }
            OutlinedButton(onClick = onPull, enabled = !state.busy && git.remoteUrl != null) { Text(stringResource(R.string.git_pull)) }
            TextButton(onClick = { dialog = GitDialog.Remote }, enabled = !state.busy) {
                Text(stringResource(if (git.remoteUrl == null) R.string.git_set_remote else R.string.git_change_remote), color = colors.accentText)
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item { ListHeader(stringResource(R.string.git_changes, git.changes.size)) }
            if (git.changes.isEmpty()) {
                item { Text(stringResource(R.string.git_no_changes), color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
            }
            items(git.changes, key = { "c:" + it.path }) { change ->
                // Paths read left to right also when the UI is Arabic.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .clickable(enabled = change.kind != ChangeKind.Deleted) { onOpenChange(change) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val label = stringResource(change.kind.labelRes)
                        Text(
                            change.kind.letter,
                            color = if (change.kind == ChangeKind.Conflict || change.kind == ChangeKind.Deleted) colors.error else colors.warning,
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp,
                            modifier = Modifier.width(22.dp).semantics { contentDescription = label },
                        )
                        Text(change.path, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.StartEllipsis)
                    }
                }
            }
            if (git.log.isNotEmpty()) item { ListHeader(stringResource(R.string.git_history)) }
            items(git.log, key = { "l:" + it.shortId }) { commit ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(commit.message, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${commit.shortId} · ${commit.author}", color = colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
    if (dialog != null && git.isRepository) Dialogs(dialog, state, viewModel) { dialog = null }
}

@Composable
private fun ListHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = WorkbenchTheme.textSecondary,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Dialogs(dialog: GitDialog?, state: SourceControlUiState, viewModel: SourceControlViewModel, onDismiss: () -> Unit) {
    when (dialog) {
        GitDialog.Account -> AccountDialog(state, onDismiss) { name, email, token, remove ->
            onDismiss()
            viewModel.saveAccount(name, email, token, remove)
        }
        GitDialog.Remote -> TextDialog(
            title = stringResource(R.string.git_remote_title),
            hint = stringResource(R.string.git_remote_hint),
            first = state.git.remoteUrl.orEmpty(),
            firstLabel = stringResource(R.string.git_remote_url),
            onDismiss = onDismiss,
        ) { url, _ ->
            onDismiss()
            viewModel.setRemote(url)
        }
        GitDialog.Clone -> TextDialog(
            title = stringResource(R.string.git_clone),
            hint = stringResource(R.string.git_clone_hint),
            first = "",
            firstLabel = stringResource(R.string.git_remote_url),
            secondLabel = stringResource(R.string.project_name),
            // "https://github.com/me/app.git" suggests the folder name "app".
            suggestSecond = { url -> url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git") },
            onDismiss = onDismiss,
        ) { url, name ->
            onDismiss()
            viewModel.clone(url, name)
        }
        null -> Unit
    }
}

@Composable
private fun TextDialog(
    title: String,
    hint: String,
    first: String,
    firstLabel: String,
    secondLabel: String? = null,
    suggestSecond: (String) -> String = { "" },
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var a by remember { mutableStateOf(first) }
    var b by remember { mutableStateOf("") }
    var bEdited by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, fontSize = 13.sp)
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = it; if (!bEdited) b = suggestSecond(it) },
                    singleLine = true,
                    label = { Text(firstLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
                if (secondLabel != null) {
                    OutlinedTextField(value = b, onValueChange = { b = it; bEdited = true }, singleLine = true, label = { Text(secondLabel) })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = a.isNotBlank() && (secondLabel == null || b.isNotBlank()), onClick = { onConfirm(a.trim(), b.trim()) }) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun AccountDialog(state: SourceControlUiState, onDismiss: () -> Unit, onSave: (String, String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(state.identity.name) }
    var email by remember { mutableStateOf(state.identity.email) }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.git_account_hint), fontSize = 13.sp)
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.git_name)) })
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.git_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.git_token)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    supportingText = { Text(stringResource(if (state.hasToken) R.string.git_token_saved else R.string.git_token_hint)) },
                )
                if (state.hasToken) {
                    TextButton(onClick = { onSave(name, email, "", true) }) {
                        Text(stringResource(R.string.git_token_remove), color = WorkbenchTheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, email, token, false) }) { Text(stringResource(R.string.dialog_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

private val ChangeKind.letter: String
    get() = when (this) {
        ChangeKind.Modified -> "M"
        ChangeKind.Added -> "A"
        ChangeKind.Deleted -> "D"
        ChangeKind.Untracked -> "U"
        ChangeKind.Conflict -> "!"
    }

private val ChangeKind.labelRes: Int
    get() = when (this) {
        ChangeKind.Modified -> R.string.git_kind_modified
        ChangeKind.Added -> R.string.git_kind_added
        ChangeKind.Deleted -> R.string.git_kind_deleted
        ChangeKind.Untracked -> R.string.git_kind_untracked
        ChangeKind.Conflict -> R.string.git_kind_conflict
    }
