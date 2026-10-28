package tech.zakomi.zakdroid.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class TerminalTab(
    val id: Int,
    val title: String,
    val kind: ShellSpec.Kind,
    val running: Boolean = true,
    val exitCode: Int? = null,
)

/**
 * Owns all terminal sessions. Sessions live here, not in the UI, so they survive
 * rotation and theme changes. Must be used from the main thread.
 */
@Singleton
class TerminalSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sessions = LinkedHashMap<Int, TerminalSession>()
    private var nextId = 1

    private val _tabs = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabs: StateFlow<List<TerminalTab>> = _tabs.asStateFlow()

    private val _activeId = MutableStateFlow<Int?>(null)
    val activeId: StateFlow<Int?> = _activeId.asStateFlow()

    /** The view currently showing a session; it receives screen updates. */
    internal var attachedView: TerminalView? = null

    fun open(spec: ShellSpec): Int {
        val id = nextId++
        val session = TerminalSession(
            spec.executable,
            spec.cwd,
            spec.argv.toTypedArray(),
            spec.env.toTypedArray(),
            TRANSCRIPT_ROWS,
            client,
        )
        session.mSessionName = id.toString()
        sessions[id] = session
        _tabs.update { it + TerminalTab(id, spec.title, spec.kind) }
        _activeId.value = id
        return id
    }

    fun select(id: Int) {
        if (sessions.containsKey(id)) _activeId.value = id
    }

    fun close(id: Int) {
        sessions.remove(id)?.finishIfRunning()
        _tabs.update { list -> list.filterNot { it.id == id } }
        if (_activeId.value == id) _activeId.value = _tabs.value.lastOrNull()?.id
    }

    fun closeAll() {
        sessions.keys.toList().forEach(::close)
    }

    fun session(id: Int): TerminalSession? = sessions[id]

    /** Re-applies the default palette to running sessions after a theme change. */
    fun refreshColors() {
        sessions.values.forEach { it.emulator?.mColors?.reset() }
        attachedView?.invalidate()
    }

    private fun idOf(session: TerminalSession): Int? = session.mSessionName?.toIntOrNull()

    private val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            val view = attachedView ?: return
            if (view.currentSession === changedSession) view.onScreenUpdated()
        }

        override fun onTitleChanged(changedSession: TerminalSession) = Unit

        override fun onSessionFinished(finishedSession: TerminalSession) {
            val id = idOf(finishedSession) ?: return
            val exit = finishedSession.exitStatus
            _tabs.update { list -> list.map { if (it.id == id) it.copy(running = false, exitCode = exit) else it } }
            attachedView?.takeIf { it.currentSession === finishedSession }?.invalidate()
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            if (text.isNullOrEmpty()) return
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("zakdroid-code terminal", text))
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrEmpty()) session?.emulator?.paste(text)
        }

        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) {
            attachedView?.invalidate()
        }

        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message.orEmpty()) }
        override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message.orEmpty()) }
        override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message.orEmpty()) }
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: TAG, message, e) }
        override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "terminal error", e) }
    }

    private companion object {
        const val TAG = "ZdTerminal"
        const val TRANSCRIPT_ROWS = 3000
    }
}
