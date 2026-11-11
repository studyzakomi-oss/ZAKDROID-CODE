package tech.zakomi.zakdroid.ui.extensions

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.editor.extensions.ExtensionRepository
import tech.zakomi.zakdroid.editor.extensions.InstalledExtension
import tech.zakomi.zakdroid.ui.common.ConfirmDialog
import tech.zakomi.zakdroid.ui.settings.ScreenScaffold
import javax.inject.Inject

sealed interface ExtensionsEvent {
    data class Installed(val name: String, val version: String) : ExtensionsEvent
    data class InstallFailed(val reason: String) : ExtensionsEvent
}

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repository: ExtensionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val extensions: StateFlow<List<InstalledExtension>> = repository.extensions

    private val _events = Channel<ExtensionsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun uninstall(id: String) {
        viewModelScope.launch { repository.uninstall(id) }
    }

    fun install(uri: Uri) {
        viewModelScope.launch {
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open the file")
                val manifest = stream.use { repository.install(it) }
                _events.send(ExtensionsEvent.Installed(manifest.name, manifest.version))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ExtensionsEvent.InstallFailed(e.message ?: e.javaClass.simpleName))
            }
        }
    }
}

/** Lists built-in and installed extensions; installs one from a .zip chosen with the system picker. */
@Composable
fun ExtensionsScreen(onBack: () -> Unit, viewModel: ExtensionsViewModel = hiltViewModel()) {
    val colors = WorkbenchTheme
    val extensions by viewModel.extensions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmUninstall by remember { mutableStateOf<InstalledExtension?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.install(uri) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                when (event) {
                    is ExtensionsEvent.Installed -> resources.getString(R.string.extensions_installed, event.name, event.version)
                    is ExtensionsEvent.InstallFailed -> resources.getString(R.string.extensions_install_failed, event.reason)
                },
            )
        }
    }

    ScreenScaffold(stringResource(R.string.extensions_title), onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.extensions_intro), color = colors.textSecondary, fontSize = 13.sp)
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    modifier = Modifier.padding(vertical = 12.dp),
                ) { Text(stringResource(R.string.extensions_install)) }

                extensions.forEach { extension ->
                    ExtensionCard(
                        extension = extension,
                        onEnabled = { viewModel.setEnabled(extension.manifest.id, it) },
                        onUninstall = { confirmUninstall = extension },
                    )
                }

                Text(
                    stringResource(R.string.extensions_format_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.accentText,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() },
                )
                Text(stringResource(R.string.extensions_format), color = colors.textSecondary, fontSize = 13.sp)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    confirmUninstall?.let { extension ->
        ConfirmDialog(
            title = stringResource(R.string.extensions_uninstall_title, extension.manifest.name),
            message = stringResource(R.string.extensions_uninstall_message),
            confirmLabel = stringResource(R.string.extensions_uninstall),
            destructive = true,
            onDismiss = { confirmUninstall = null },
            onConfirm = {
                confirmUninstall = null
                viewModel.uninstall(extension.manifest.id)
            },
        )
    }
}

@Composable
private fun ExtensionCard(extension: InstalledExtension, onEnabled: (Boolean) -> Unit, onUninstall: () -> Unit) {
    val colors = WorkbenchTheme
    val manifest = extension.manifest
    Column(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .background(colors.sideBar, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(extension.enabled, role = Role.Switch, onValueChange = onEnabled),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(manifest.name, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                Text(manifest.id, color = colors.textSecondary, fontSize = 12.sp)
            }
            Switch(checked = extension.enabled, onCheckedChange = null)
        }
        if (manifest.description.isNotEmpty()) Text(manifest.description, color = colors.textPrimary, fontSize = 13.sp)
        val details = buildList {
            add(stringResource(R.string.extensions_version, manifest.version))
            if (manifest.author.isNotEmpty()) add(stringResource(R.string.extensions_by, manifest.author))
            if (manifest.license.isNotEmpty()) add(manifest.license)
            if (extension.builtIn) add(stringResource(R.string.extensions_built_in))
        }
        Text(details.joinToString(" · "), color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        if (manifest.snippets.isNotEmpty()) {
            Text(
                stringResource(R.string.extensions_snippet_languages, manifest.snippets.map { it.language }.distinct().joinToString(", ")),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }
        if (!extension.builtIn) {
            TextButton(onClick = onUninstall) { Text(stringResource(R.string.extensions_uninstall), color = colors.error) }
        }
    }
}
