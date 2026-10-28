package tech.zakomi.zakdroid.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.core.settings.AppSettings
import tech.zakomi.zakdroid.core.settings.SettingsRepository
import tech.zakomi.zakdroid.core.workspace.StoragePaths
import tech.zakomi.zakdroid.core.workspace.WorkspaceKind
import tech.zakomi.zakdroid.runtime.RuntimeManager
import tech.zakomi.zakdroid.runtime.RuntimeState
import tech.zakomi.zakdroid.state.WorkspaceSession
import tech.zakomi.zakdroid.terminal.ExtraKeysState
import tech.zakomi.zakdroid.terminal.TerminalSessionManager
import tech.zakomi.zakdroid.terminal.TerminalTab
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class TerminalViewModel @Inject constructor(
    val manager: TerminalSessionManager,
    private val runtime: RuntimeManager,
    private val session: WorkspaceSession,
    private val paths: StoragePaths,
    private val settings: SettingsRepository,
) : ViewModel() {

    val tabs: StateFlow<List<TerminalTab>> = manager.tabs
    val activeId: StateFlow<Int?> = manager.activeId
    val runtimeState: StateFlow<RuntimeState> = runtime.state

    /** Survives configuration changes together with the sessions. */
    val keys = ExtraKeysState()

    private var shellCount = 0

    /** Opens a login shell in the workspace folder. Returns false if the runtime is not ready. */
    fun newShell(): Boolean {
        if (runtime.state.value != RuntimeState.Ready) return false
        manager.open(runtime.shell("sh ${++shellCount}", workspaceGuestDir()))
        return true
    }

    fun select(id: Int) = manager.select(id)

    fun close(id: Int) = manager.close(id)

    fun zoom(scale: Float) {
        viewModelScope.launch {
            settings.update { s ->
                val size = (s.terminalFontSizeSp * scale).roundToInt().coerceIn(AppSettings.FONT_SIZE_RANGE)
                s.copy(terminalFontSizeSp = size)
            }
        }
    }

    private fun workspaceGuestDir(): String {
        val ws = session.current.value ?: return StoragePaths.GUEST_HOME
        if (ws.kind != WorkspaceKind.Internal) return StoragePaths.GUEST_HOME
        val dir = File((ws.root as FileRef.Local).path).canonicalPath
        val home = paths.homeDir.canonicalPath
        return if (dir.startsWith(home)) StoragePaths.GUEST_HOME + dir.removePrefix(home) else StoragePaths.GUEST_HOME
    }
}
