package tech.zakomi.zakdroid.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
    data class Message(val text: String) : EditorEvent
    data class ConfirmClose(val tab: OpenTab) : EditorEvent
    data object RuntimeNotReady : EditorEvent
    data object NothingToRun : EditorEvent
    data class UnsupportedRun(val language: EditorLanguage) : EditorEvent
    data object RunStarted : EditorEvent
}

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
    val textMate: TextMateSetup,
) : ViewModel() {

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

    init {
        viewModelScope.launch {
            session.current.collect { ws ->
                if (ws == null || ws.id != restoredFor) {
                    documents.clear()
                    restoredFor = ws?.id
                    if (ws != null) restoreTabs(ws)
                }
            }
        }
        viewModelScope.launch {
            combine(documents.tabs, documents.activeKey) { _, _ -> }.drop(1).collect { schedulePersist() }
        }
    }

    fun open(ref: FileRef, name: String, relativePath: String, activate: Boolean = true) {
        viewModelScope.launch { openInternal(ref, name, relativePath, activate) }
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
                _events.send(EditorEvent.Message("\"$name\" no longer exists"))
                return
            }
            when (val result = loader.load(entry)) {
                is LoadResult.Loaded -> documents.add(result.document, relativePath, activate)
                is LoadResult.Failed -> _events.send(EditorEvent.Message("$name: ${result.message}"))
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
            documents.remove(key)
        }
    }

    fun closeDiscarding(key: String) = documents.remove(key)

    fun saveAndClose(key: String) {
        viewModelScope.launch {
            if (save(key)) documents.remove(key)
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

    private suspend fun save(key: String): Boolean {
        val tab = documents.find(key) ?: return false
        return try {
            loader.save(tab.document)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.send(EditorEvent.Message("Cannot save ${tab.name}: ${e.message}"))
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
                _events.send(EditorEvent.Message("Cannot run ${tab.name}: ${e.message}"))
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
        val HEADER_EXTENSIONS = setOf("h", "hh", "hpp", "hxx", "h++", "inl", "ipp", "tpp", "txx")
    }
}
