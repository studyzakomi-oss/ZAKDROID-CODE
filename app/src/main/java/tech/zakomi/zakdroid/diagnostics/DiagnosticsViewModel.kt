package tech.zakomi.zakdroid.diagnostics

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.zakomi.zakdroid.BuildConfig
import tech.zakomi.zakdroid.runtime.RuntimeManager
import tech.zakomi.zakdroid.runtime.RuntimeState
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: RuntimeManager,
) : ViewModel() {

    /** Plain-text report for bug reports. Contains no file contents or personal data. */
    fun report(): String = buildString {
        appendLine("zakdroid-code diagnostics")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR}/${BuildConfig.BUILD_TYPE}")
        appendLine("Package: ${context.packageName}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Runtime: ${describe(runtime.state.value)}")
        appendLine("Runtime build: ${runtime.description}")
        appendLine()
        appendLine("Last crash:")
        appendLine(CrashLog.lastCrash(context) ?: "none")
        appendLine()
        appendLine("Installer log:")
        append(runtime.installLog() ?: "none")
    }

    private fun describe(state: RuntimeState): String = when (state) {
        is RuntimeState.NotInstalled -> if (state.canResume) "Not installed (partial install, resumable)" else "Not installed"
        is RuntimeState.Installing -> "Installing ${state.percent}% (${state.step}${state.detail?.let { ": $it" } ?: ""})"
        RuntimeState.Ready -> "Ready"
        RuntimeState.Stopping -> "Stopping (cleaning up a cancelled run)"
        is RuntimeState.Error -> "Error ${state.kind} at ${state.step}: ${state.message}"
    }
}
