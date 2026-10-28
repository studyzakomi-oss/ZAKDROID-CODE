package tech.zakomi.zakdroid.ui.explorer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.zakomi.zakdroid.core.fs.FileEntry
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.core.fs.FileSystem
import tech.zakomi.zakdroid.core.workspace.ProjectTemplate
import tech.zakomi.zakdroid.core.workspace.Workspace
import tech.zakomi.zakdroid.core.workspace.WorkspaceRepository
import tech.zakomi.zakdroid.state.OpenDocumentsStore
import tech.zakomi.zakdroid.state.WorkspaceSession
import javax.inject.Inject

/** A visible row of the file tree (or a search result). */
data class TreeRow(
    val entry: FileEntry,
    val depth: Int,
    val expanded: Boolean,
    val relativePath: String,
    val parent: FileRef,
)

data class ExplorerUiState(
    val workspace: Workspace? = null,
    val rows: List<TreeRow> = emptyList(),
    val loading: Boolean = false,
    val query: String = "",
    /** Null when not searching. */
    val results: List<TreeRow>? = null,
    val searching: Boolean = false,
    val cut: TreeRow? = null,
)

sealed interface ExplorerMessage {
    data class Error(val text: String) : ExplorerMessage
    data object SaveFolderFilesFirst : ExplorerMessage
    data object AccessLost : ExplorerMessage
}

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val fs: FileSystem,
    private val repository: WorkspaceRepository,
    private val session: WorkspaceSession,
    private val documents: OpenDocumentsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    val recent: StateFlow<List<Workspace>> = repository.recentWorkspaces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _projects = MutableStateFlow<List<Workspace>>(emptyList())
    val projects: StateFlow<List<Workspace>> = _projects.asStateFlow()

    private val _messages = Channel<ExplorerMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val expanded = HashSet<String>()
    private val children = HashMap<String, List<FileEntry>>()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            session.current.collect { ws ->
                expanded.clear()
                children.clear()
                _state.value = ExplorerUiState(workspace = ws)
                if (ws != null) reload()
            }
        }
        refreshProjects()
    }

    fun refreshProjects() {
        viewModelScope.launch { _projects.value = repository.internalProjects() }
    }

    // ---- Workspaces -------------------------------------------------------------------------

    fun openWorkspace(workspace: Workspace) {
        viewModelScope.launch {
            if (!repository.hasAccess(workspace)) {
                _messages.send(ExplorerMessage.AccessLost)
                repository.forget(workspace)
                return@launch
            }
            session.open(workspace)
        }
    }

    fun openSafFolder(treeUri: Uri, displayName: String) {
        guarded { session.open(repository.fromSafTree(treeUri, displayName)) }
    }

    fun createProject(name: String, template: ProjectTemplate?, onCreated: (Workspace) -> Unit = {}) {
        guarded {
            val ws = repository.createInternalProject(name.trim(), template)
            refreshProjects()
            session.open(ws)
            onCreated(ws)
        }
    }

    fun closeWorkspace() = session.close()

    fun forget(workspace: Workspace) {
        viewModelScope.launch { repository.forget(workspace) }
    }

    // ---- Tree -------------------------------------------------------------------------------

    fun reload() {
        val ws = _state.value.workspace ?: return
        guarded {
            _state.update { it.copy(loading = true) }
            children.clear()
            loadChildren(ws.root)
            expanded.toList().forEach { key -> runCatching { loadChildren(FileRef.fromKey(key)) } }
            rebuild()
        }
    }

    fun toggle(row: TreeRow) {
        if (!row.entry.isDirectory) return
        val key = row.entry.ref.key
        if (!expanded.remove(key)) {
            expanded += key
            guarded {
                if (key !in children) loadChildren(row.entry.ref)
                rebuild()
            }
        } else {
            rebuild()
        }
    }

    private suspend fun loadChildren(dir: FileRef) {
        children[dir.key] = fs.list(dir)
    }

    private fun rebuild() {
        val ws = _state.value.workspace ?: return
        val rows = ArrayList<TreeRow>()
        fun visit(dir: FileRef, depth: Int, prefix: String) {
            for (entry in children[dir.key].orEmpty()) {
                val rel = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                val isOpen = entry.isDirectory && entry.ref.key in expanded
                rows += TreeRow(entry, depth, isOpen, rel, dir)
                if (isOpen) visit(entry.ref, depth + 1, rel)
            }
        }
        visit(ws.root, 0, "")
        _state.update { it.copy(rows = rows, loading = false) }
    }

    // ---- Search -----------------------------------------------------------------------------

    /** Recursive file-name search across the workspace (case-insensitive, substring). */
    fun search(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        val ws = _state.value.workspace ?: return
        if (query.isBlank()) {
            _state.update { it.copy(results = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            _state.update { it.copy(searching = true) }
            val needle = query.trim().lowercase()
            val found = ArrayList<TreeRow>()
            var visited = 0
            suspend fun walk(dir: FileRef, prefix: String) {
                val entries = runCatching { children[dir.key] ?: fs.list(dir) }.getOrDefault(emptyList())
                for (entry in entries) {
                    if (visited++ > SEARCH_LIMIT || found.size >= MAX_RESULTS) return
                    val rel = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                    if (entry.isDirectory) {
                        if (entry.name !in SKIPPED_DIRS) walk(entry.ref, rel)
                    } else if (needle in entry.name.lowercase()) {
                        found += TreeRow(entry, 0, false, rel, dir)
                    }
                }
            }
            walk(ws.root, "")
            _state.update { it.copy(results = found, searching = false) }
        }
    }

    // ---- File operations --------------------------------------------------------------------

    fun createFile(parent: FileRef, parentRelative: String, name: String, onCreated: (TreeRow) -> Unit) = guarded {
        val ref = fs.createFile(parent, name.trim())
        expandAndReload(parent)
        val entry = fs.stat(ref) ?: return@guarded
        onCreated(TreeRow(entry, 0, false, join(parentRelative, entry.name), parent))
    }

    fun createFolder(parent: FileRef, name: String) = guarded {
        fs.createDirectory(parent, name.trim())
        expandAndReload(parent)
    }

    fun rename(row: TreeRow, newName: String) = guarded {
        if (row.entry.isDirectory && blockedByDirtyFiles(row)) return@guarded
        val newRef = fs.rename(row.entry.ref, newName.trim())
        val newRel = join(row.relativePath.substringBeforeLast('/', ""), newName.trim())
        afterMove(row, newRef, newName.trim(), newRel)
    }

    fun delete(row: TreeRow) = guarded {
        if (row.entry.isDirectory && blockedByDirtyFiles(row)) return@guarded
        fs.delete(row.entry.ref)
        documents.under(row.relativePath).forEach { documents.remove(it.key) }
        expanded.remove(row.entry.ref.key)
        reload()
    }

    fun cut(row: TreeRow) {
        _state.update { it.copy(cut = row) }
    }

    fun cancelCut() {
        _state.update { it.copy(cut = null) }
    }

    /** Moves the cut entry into [target] (a folder) or into the workspace root when null. */
    fun pasteInto(target: TreeRow?) = guarded {
        val moving = _state.value.cut ?: return@guarded
        val ws = _state.value.workspace ?: return@guarded
        val destination = target?.entry?.ref ?: ws.root
        if (moving.entry.isDirectory && blockedByDirtyFiles(moving)) return@guarded
        val newRef = fs.move(moving.entry.ref, moving.parent, destination)
        _state.update { it.copy(cut = null) }
        afterMove(moving, newRef, moving.entry.name, join(target?.relativePath.orEmpty(), moving.entry.name))
        expanded += destination.key
        reload()
    }

    private suspend fun afterMove(row: TreeRow, newRef: FileRef, newName: String, newRelative: String) {
        if (row.entry.isDirectory) {
            // Child references change with their folder; close (clean) open files inside it.
            documents.under(row.relativePath).forEach { documents.remove(it.key) }
            expanded.remove(row.entry.ref.key)
        } else {
            documents.onFileMoved(row.entry.ref.key, newRef, newName, newRelative)
        }
        reload()
    }

    private suspend fun blockedByDirtyFiles(row: TreeRow): Boolean {
        val dirty = documents.under(row.relativePath).any { it.dirty }
        if (dirty) _messages.send(ExplorerMessage.SaveFolderFilesFirst)
        return dirty
    }

    private suspend fun expandAndReload(dir: FileRef) {
        val ws = _state.value.workspace ?: return
        if (dir != ws.root) expanded += dir.key
        loadChildren(dir)
        rebuild()
    }

    private fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

    private fun guarded(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false) }
            _messages.send(ExplorerMessage.Error(e.message ?: e.javaClass.simpleName))
        }
    }

    private companion object {
        const val SEARCH_LIMIT = 20_000
        const val MAX_RESULTS = 500
        val SKIPPED_DIRS = setOf(".git", "node_modules", "build", "__pycache__", ".venv", "venv")
    }
}
