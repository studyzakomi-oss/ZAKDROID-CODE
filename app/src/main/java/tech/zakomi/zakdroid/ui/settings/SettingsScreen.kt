package tech.zakomi.zakdroid.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.settings.AppSettings
import tech.zakomi.zakdroid.core.settings.ThemeId
import tech.zakomi.zakdroid.runtime.RuntimeState
import tech.zakomi.zakdroid.runtime.acceptsInstallRequest
import tech.zakomi.zakdroid.ui.common.ConfirmDialog
import tech.zakomi.zakdroid.ui.runtime.RuntimeViewModel
import tech.zakomi.zakdroid.ui.runtime.rememberWithNotificationPermission
import tech.zakomi.zakdroid.ui.runtime.runtimeStatusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val colors = WorkbenchTheme
    Scaffold(
        containerColor = colors.editor,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.activityBar,
                    titleContentColor = colors.statusBarText,
                    navigationIconContentColor = colors.statusBarText,
                ),
            )
        },
        content = content,
    )
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    runtimeVm: RuntimeViewModel = hiltViewModel(),
) {
    val colors = WorkbenchTheme
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val runtime by runtimeVm.state.collectAsStateWithLifecycle()
    var confirmReinstall by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val install = rememberWithNotificationPermission(runtimeVm::install)
    val repair = rememberWithNotificationPermission(runtimeVm::repair)

    ScreenScaffold(stringResource(R.string.settings_title), onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Section(stringResource(R.string.settings_appearance))
                Text(stringResource(R.string.settings_theme), color = colors.textSecondary, fontSize = 13.sp)
                Column(Modifier.selectableGroup()) {
                    ThemeId.entries.forEach { theme ->
                        val label = stringResource(if (theme == ThemeId.NavyDark) R.string.theme_navy_dark else R.string.theme_light)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(settings.theme == theme, role = Role.RadioButton) { viewModel.update { it.copy(theme = theme) } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.theme == theme, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = colors.textPrimary)
                        }
                    }
                }
                LanguageRow(
                    onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        }
                    } else {
                        null
                    },
                )

                Section(stringResource(R.string.settings_editor))
                Stepper(stringResource(R.string.settings_font_size), settings.editorFontSizeSp) { size ->
                    viewModel.update { it.copy(editorFontSizeSp = size) }
                }
                SwitchRow(stringResource(R.string.settings_ligatures), stringResource(R.string.settings_ligatures_hint), settings.ligatures) { on ->
                    viewModel.update { it.copy(ligatures = on) }
                }
                SwitchRow(stringResource(R.string.settings_word_wrap), null, settings.wordWrap) { on ->
                    viewModel.update { it.copy(wordWrap = on) }
                }
                Text(stringResource(R.string.settings_tab_size), color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.selectableGroup()) {
                    AppSettings.TAB_SIZES.forEach { size ->
                        Row(
                            Modifier.heightIn(min = 48.dp).selectable(settings.tabSize == size, role = Role.RadioButton) {
                                viewModel.update { it.copy(tabSize = size) }
                            }.padding(end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.tabSize == size, onClick = null)
                            Text(size.toString(), color = colors.textPrimary, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                Section(stringResource(R.string.settings_terminal))
                Stepper(stringResource(R.string.settings_font_size), settings.terminalFontSizeSp) { size ->
                    viewModel.update { it.copy(terminalFontSizeSp = size) }
                }

                Section(stringResource(R.string.settings_runtime))
                Text(runtimeStatusLabel(runtime), color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                Text(runtimeVm.description, color = colors.textSecondary, fontSize = 13.sp)
                if (runtime is RuntimeState.Error) {
                    Text((runtime as RuntimeState.Error).message, color = colors.error, fontSize = 13.sp, maxLines = 4)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    when (val s = runtime) {
                        is RuntimeState.NotInstalled -> OutlinedButton(onClick = install) {
                            Text(stringResource(if (s.canResume) R.string.runtime_resume else R.string.runtime_install))
                        }
                        is RuntimeState.Installing -> OutlinedButton(onClick = runtimeVm::cancel) { Text(stringResource(R.string.runtime_cancel)) }
                        RuntimeState.Stopping -> OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.runtime_stopping)) }
                        is RuntimeState.Error -> OutlinedButton(onClick = install) { Text(stringResource(R.string.runtime_retry)) }
                        RuntimeState.Ready -> Unit
                    }
                    if (runtime.acceptsInstallRequest()) {
                        OutlinedButton(onClick = repair) { Text(stringResource(R.string.runtime_repair)) }
                        OutlinedButton(onClick = { confirmReinstall = true }) { Text(stringResource(R.string.runtime_reinstall)) }
                    }
                }

                Section(stringResource(R.string.about_title))
                Text(
                    stringResource(R.string.settings_about),
                    color = colors.accentText,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOpenAbout).padding(vertical = 14.dp),
                )
            }
        }
    }

    if (confirmReinstall) {
        ConfirmDialog(
            title = stringResource(R.string.runtime_reinstall_title),
            message = stringResource(R.string.runtime_reinstall_message),
            confirmLabel = stringResource(R.string.runtime_reinstall),
            destructive = true,
            onDismiss = { confirmReinstall = false },
            onConfirm = {
                confirmReinstall = false
                runtimeVm.reinstall()
            },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = WorkbenchTheme.accentText,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun LanguageRow(onClick: (() -> Unit)?) {
    val colors = WorkbenchTheme
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Text(stringResource(R.string.settings_language), color = colors.textPrimary)
        Text(
            stringResource(if (onClick != null) R.string.settings_language_hint else R.string.settings_language_unsupported),
            color = colors.textSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = WorkbenchTheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary)
            if (subtitle != null) Text(subtitle, color = colors.textSecondary, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Stepper(title: String, value: Int, onChange: (Int) -> Unit) {
    val colors = WorkbenchTheme
    val range = AppSettings.FONT_SIZE_RANGE
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = colors.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Outlined.Remove, stringResource(R.string.settings_decrease), tint = colors.textPrimary)
        }
        Text(
            stringResource(R.string.settings_font_size_value, value),
            color = colors.textPrimary,
            modifier = Modifier.background(colors.sideBar).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Outlined.Add, stringResource(R.string.settings_increase), tint = colors.textPrimary)
        }
    }
}
