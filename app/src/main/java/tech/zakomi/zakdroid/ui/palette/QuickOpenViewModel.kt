package tech.zakomi.zakdroid.ui.palette

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.fs.FileSystem
import tech.zakomi.zakdroid.core.search.FuzzyMatcher
import tech.zakomi.zakdroid.core.search.WorkspaceFile
import tech.zakomi.zakdroid.core.search.WorkspaceFiles
import tech.zakomi.zakdroid.state.OpenDocumentsStore
import tech.zakomi.zakdroid.state.WorkspaceSession
import javax.inject.Inject

/** A Quick Open row: [positions] are the matched characters of the relative path. */
data class FileHit(val file: WorkspaceFile, val positions: List<Int>)

data class QuickOpenState(
    val hits: List<FileHit> = emptyList(),
    val indexing: Boolean = false,
    val hasWorkspace: Boolean = false,
)

/** Quick Open (Ctrl+P): lists the workspace's files once per opening and fuzzy-filters them. */
@HiltViewModel
class QuickOpenViewModel @Inject constructor(
    private val fs: FileSystem,
    private val session: WorkspaceSession,
    private val documents: OpenDocumentsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickOpenState())
    val state: StateFlow<QuickOpenState> = _state.asStateFlow()

    private var files: List<WorkspaceFile> = emptyList()
    private var query = ""
    private var indexJob: Job? = null
    private var filterJob: Job? = null

    /** Call when the picker opens: files created since the last time must show up. */
    fun refresh() {
        val workspace = session.current.value
        indexJob?.cancel()
        if (workspace == null) {
            files = emptyList()
            _state.value = QuickOpenState()
            return
        }
        _state.value = _state.value.copy(indexing = true, hasWorkspace = true)
        indexJob = viewModelScope.launch {
            files = WorkspaceFiles.list(fs, workspace.root).files
            _state.value = _state.value.copy(indexing = false)
            filter()
        }
    }

    fun setQuery(text: String) {
        query = text.trim()
        filter()
    }

    private fun filter() {
        filterJob?.cancel()
        val all = files
        val q = query
        val openKeys = documents.tabs.value.map { it.key }
        filterJob = viewModelScope.launch {
            val hits = withContext(Dispatchers.Default) {
                if (q.isEmpty()) {
                    // Like VS Code: open editors first, then the rest of the workspace.
                    val open = openKeys.mapNotNull { key -> all.firstOrNull { it.entry.ref.key == key } }
                    (open + all.filter { it.entry.ref.key !in openKeys }).take(MAX_HITS).map { FileHit(it, emptyList()) }
                } else {
                    all.mapNotNull { file -> FuzzyMatcher.matchPath(q, file.relativePath)?.let { file to it } }
                        .sortedWith(compareByDescending<Pair<WorkspaceFile, tech.zakomi.zakdroid.core.search.FuzzyMatch>> { it.second.score }.thenBy { it.first.relativePath })
                        .take(MAX_HITS)
                        .map { FileHit(it.first, it.second.positions) }
                }
            }
            _state.value = _state.value.copy(hits = hits)
        }
    }

    private companion object {
        const val MAX_HITS = 60
    }
}
