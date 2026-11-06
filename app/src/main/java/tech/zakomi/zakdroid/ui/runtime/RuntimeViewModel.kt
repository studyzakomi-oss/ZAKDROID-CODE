package tech.zakomi.zakdroid.ui.runtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tech.zakomi.zakdroid.runtime.RuntimeManager
import tech.zakomi.zakdroid.runtime.RuntimeState
import tech.zakomi.zakdroid.terminal.TerminalSessionManager
import javax.inject.Inject

@HiltViewModel
class RuntimeViewModel @Inject constructor(
    private val runtime: RuntimeManager,
    terminals: TerminalSessionManager,
) : ViewModel() {
    val state: StateFlow<RuntimeState> = runtime.state
    val description: String get() = runtime.description
    val packages: List<String> get() = runtime.packages

    /** Running terminal sessions; Repair and Reinstall close them. */
    val runningTerminals: StateFlow<Int> = terminals.tabs
        .map { tabs -> tabs.count { it.running } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun install() = runtime.install()
    fun cancel() = runtime.cancel()
    fun repair() = runtime.repair()
    fun reinstall() = runtime.reinstall()
}
