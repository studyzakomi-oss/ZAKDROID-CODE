package tech.zakomi.zakdroid.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.settings.AppSettings
import tech.zakomi.zakdroid.core.settings.AutoSave
import tech.zakomi.zakdroid.core.settings.SettingsRepository
import tech.zakomi.zakdroid.editor.commands.DocumentCleanup
import tech.zakomi.zakdroid.editor.outline.DocumentSymbol
import tech.zakomi.zakdroid.editor.outline.SymbolOutline
import tech.zakomi.zakdroid.core.data.OpenTabEntity
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.core.fs.FileSystem
import tech.zakomi.zakdroid.core.workspace.Workspace
import tech.zakomi.zakdroid.core.workspace.WorkspaceRepository
import tech.zakomi.zakdroid.editor.EditorLanguage
import tech.zakomi.zakdroid.editor.document.DocumentLoader
import tech.zakomi.zakdroid.editor.document.LoadResult
import tech.zakomi.zakdroid.editor.textmate.TextMateSetup
import tech.zakomi.zakdroid.runtime.RunLanguage
import tech.zakomi.zakdroid.runtime.RunTarget
import tech.zakomi.zakdroid.runtime.RuntimeManager
import tech.zakomi.zakdroid.runtime.RuntimeState
import tech.zakomi.zakdroid.runtime.WorkspaceStager
import tech.zakomi.zakdroid.state.OpenDocumentsStore
import tech.zakomi.zakdroid.state.OpenTab
import tech.zakomi.zakdroid.state.WorkspaceSession
import tech.zakomi.zakdroid.terminal.TerminalSessionManager
import javax.inject.Inject

sealed interface EditorEvent {
    data class FileMissing(val name: String) : EditorEvent
    data class OpenFailed(val name: String, val reason: LoadResult.Reason, val detail: String) : EditorEvent
    data class SaveFailed(val name: String, val detail: String) : EditorEvent
    data class RunFailed(val name: String, val detail: String) : EditorEvent
    data class ConfirmClose(val tab: OpenTab) : EditorEvent
    data object RuntimeNotReady : EditorEvent
    data object NothingToRun : EditorEvent
    data class UnsupportedRun(val language: EditorLanguage) : EditorEvent
    data object RunStarted : EditorEvent

    /** Show a 0-based position of an open file (Quick Open, search result, symbol). */
    data class Reveal(val key: String, val line: Int, val startColumn: Int, val endColumn: Int) : EditorEvent
}

/** A closed tab that "Reopen closed editor" can bring back. */
private data class ClosedTab(val ref: FileRef, val name: String, val relativePath: String)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documents: OpenDocumentsStore,
    private val loader: DocumentLoader,
    private val fs: FileSystem,
    private val session: WorkspaceSession,
    private val repository: WorkspaceRepository,
    private val runtime: RuntimeManager,
    private val stager: WorkspaceStager,
    private val terminals: TerminalSessionManager,
    settingsRepository: SettingsRepository,
    val textMate: TextMateSetup,
) : ViewModel() {

    private val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val tabs: StateFlow<List<OpenTab>> = documents.tabs
    val activeKey: StateFlow<String?> = documents.activeKey

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var restoredFor: String? = null
    private var persistJob: Job? = null
    private val saveMutex = Mutex()
    private val recentlyClosed = ArrayDeque<ClosedTab>()

    init {
        viewModelScope.launch {
            session.current.collect { ws ->
                if (ws == null || ws.id != restoredFor) {
                    documents.clear()
                    recentlyClosed.clear()
                    restoredFor = ws?.id
                    if (ws != null) restoreTabs(ws)
                }
            }
        }
        viewModelScope.launch {
            combine(documents.tabs, documents.activeKey) { _, _ -> }.drop(1).collect { schedulePersist() }
        }
        viewModelScope.launch { autoSaveLoop() }
    }

    /**
     * Auto save "after delay": while a file is unsaved, write it once typing has paused.
     * A failed write stops the loop until the next change, so one error is reported once.
     */
    private suspend fun autoSaveLoop() {
        combine(settings.map { it.autoSave }, documents.tabs.map { tabs -> tabs.any { it.dirty } }) { mode, dirty ->
            mode == AutoSave.AfterDelay && dirty
        }.distinctUntilChanged().collectLatest { pending ->
            if (!pending) return@collectLatest
            while (documents.tabs.value.any { it.dirty }) {
                val before = documents.tabs.value.filter { it.dirty }.associate { it.key to it.document.content.documentVersion }
                delay(AppSettings.AUTO_SAVE_DELAY_MS)
                // Save only files that were not edited during the delay; the others wait for the next round.
                val idle = documents.tabs.value.filter { it.dirty && before[it.key] == it.document.content.documentVersion }
                if (idle.any { !save(it.key, cleanUp = false) }) return@collectLatest
            }
        }
    }

    fun open(ref: FileRef, name: String, relativePath: String, activate: Boolean = true) {
        viewModelScope.launch { openInternal(ref, name, relativePath, activate) }
    }

    /** Opens the file (or switches to its tab) and jumps to a 0-based position, selecting up to [endColumn]. */
    fun openAt(ref: FileRef, name: String, relativePath: String, line: Int, startColumn: Int = 0, endColumn: Int = startColumn) {
        viewModelScope.launch {
            openInternal(ref, name, relativePath, activate = true)
            if (documents.find(ref.key) != null) _events.send(EditorEvent.Reveal(ref.key, line, startColumn, endColumn))
        }
    }

    /** Definitions in the active file, for "Go to Symbol in File". */
    suspend fun symbolsOfActive(): List<DocumentSymbol> {
        val tab = documents.active ?: return emptyList()
        val text = tab.document.content.toString()
        return withContext(Dispatchers.Default) { SymbolOutline.of(tab.document.language, text) }
    }

    private suspend fun openInternal(ref: FileRef, name: String, relativePath: String, activate: Boolean) {
        if (documents.find(ref.key) != null) {
            if (activate) documents.activate(ref.key)
            return
        }
        _loading.value = true
        try {
            val entry = fs.stat(ref)
            if (entry == null) {
                _events.send(EditorEvent.FileMissing(name))
                return
            }
            when (val result = loader.load(entry)) {
                is LoadResult.Loaded -> documents.add(result.document, relativePath, activate)
                is LoadResult.Failed -> _events.send(EditorEvent.OpenFailed(name, result.reason, result.message))
            }
        } finally {
            _loading.value = false
        }
    }

    fun activate(key: String) = documents.activate(key)

    fun nextTab() = documents.activateOffset(1)

    fun previousTab() = documents.activateOffset(-1)

    fun requestClose(key: String) {
        val tab = documents.find(key) ?: return
        if (tab.dirty) {
            viewModelScope.launch { _events.send(EditorEvent.ConfirmClose(tab)) }
        } else {
            close(key)
        }
    }

    /** Closes several tabs; each unsaved one asks first, like closing it by hand. */
    private fun requestCloseAll(tabs: List<OpenTab>) = tabs.forEach { requestClose(it.key) }

    fun closeOthers(key: String) = requestCloseAll(documents.tabs.value.filter { it.key != key })

    fun closeToTheRight(key: String) {
        val list = documents.tabs.value
        val index = list.indexOfFirst { it.key == key }
        if (index >= 0) requestCloseAll(list.drop(index + 1))
    }

    fun closeSaved() = requestCloseAll(documents.tabs.value.filter { !it.dirty })

    fun closeAll() = requestCloseAll(documents.tabs.value)

    fun closeDiscarding(key: String) = close(key)

    fun saveAndClose(key: String) {
        viewModelScope.launch {
            if (save(key)) close(key)
        }
    }

    private fun close(key: String) {
        val tab = documents.find(key) ?: return
        recentlyClosed.remove(ClosedTab(tab.document.ref, tab.name, tab.relativePath))
        recentlyClosed.addLast(ClosedTab(tab.document.ref, tab.name, tab.relativePath))
        while (recentlyClosed.size > MAX_RECENTLY_CLOSED) recentlyClosed.removeFirst()
        documents.remove(key)
    }

    /** Ctrl+Shift+T: reopens the tab closed last, skipping files that are open again. */
    fun reopenClosed() {
        while (recentlyClosed.isNotEmpty()) {
            val closed = recentlyClosed.removeLast()
            if (documents.find(closed.ref.key) != null) continue
            open(closed.ref, closed.name, closed.relativePath)
            return
        }
    }

    fun saveActive() {
        val key = documents.activeKey.value ?: return
        viewModelScope.launch { save(key) }
    }

    fun saveAll() {
        viewModelScope.launch { saveAllInternal() }
    }

    private suspend fun saveAllInternal(): Boolean {
        var ok = true
        documents.tabs.value.filter { it.dirty }.forEach { ok = save(it.key) && ok }
        return ok
    }

    /**
     * Writes one file. [cleanUp] applies the save-time settings (trim trailing whitespace,
     * final newline); auto save skips them so text does not change under the cursor.
     */
    private suspend fun save(key: String, cleanUp: Boolean = true): Boolean = saveMutex.withLock {
        val tab = documents.find(key) ?: return false
        try {
            if (cleanUp) {
                val current = settings.value
                if (current.trimTrailingWhitespace) DocumentCleanup.trimTrailingWhitespace(tab.document.content)
                if (current.insertFinalNewline) DocumentCleanup.ensureFinalNewline(tab.document.content)
            }
            loader.save(tab.document)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.send(EditorEvent.SaveFailed(tab.name, e.detail()))
            false
        }
    }

    /** Saves all files, then builds and runs the active file in a new terminal tab. */
    fun runActive() {
        val tab = documents.active
        val workspace = session.current.value
        viewModelScope.launch {
            if (tab == null || workspace == null) {
                _events.send(EditorEvent.NothingToRun)
                return@launch
            }
            val language = when (tab.document.language) {
                EditorLanguage.C -> RunLanguage.C
                EditorLanguage.Cpp -> RunLanguage.Cpp
                EditorLanguage.Python -> RunLanguage.Python
                EditorLanguage.PlainText -> null
            }
            if (language == null || (language != RunLanguage.Python && tab.name.substringAfterLast('.').lowercase() in HEADER_EXTENSIONS)) {
                _events.send(EditorEvent.UnsupportedRun(tab.document.language))
                return@launch
            }
            if (runtime.state.value != RuntimeState.Ready) {
                _events.send(EditorEvent.RuntimeNotReady)
                return@launch
            }
            _running.value = true
            try {
                if (!saveAllInternal()) return@launch
                val guestFile = stager.prepare(workspace, tab.document.ref, tab.relativePath)
                val target = RunTarget(language, tab.name, guestFile, guestFile.substringBeforeLast('/'))
                terminals.open(runtime.run(target))
                _events.send(EditorEvent.RunStarted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(EditorEvent.RunFailed(tab.name, e.detail()))
            } finally {
                _running.value = false
            }
        }
    }

    private suspend fun restoreTabs(workspace: Workspace) {
        val saved = repository.savedTabs(workspace)
        saved.forEach { openInternal(FileRef.fromKey(it.fileKey), it.name, it.relativePath, activate = it.isActive) }
        if (documents.activeKey.value == null) documents.tabs.value.firstOrNull()?.let { documents.activate(it.key) }
    }

    private fun schedulePersist() {
        val ws = session.current.value ?: return
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(500)
            val active = documents.activeKey.value
            val entities = documents.tabs.value.mapIndexed { i, tab ->
                OpenTabEntity(ws.id, tab.key, tab.name, tab.relativePath, i, tab.key == active, 0, 0)
            }
            repository.saveTabs(ws, entities)
        }
    }

    private companion object {
        const val MAX_RECENTLY_CLOSED = 20
        val HEADER_EXTENSIONS = setOf("h", "hh", "hpp", "hxx", "h++", "inl", "ipp", "tpp", "txx")
    }
}

/** The exception's own text (from the system, not translated), for after a localized prefix. */
internal fun Exception.detail(): String = message ?: javaClass.simpleName
