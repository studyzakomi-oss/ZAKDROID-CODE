package tech.zakomi.zakdroid.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.fs.FileSystem
import tech.zakomi.zakdroid.core.search.TextMatch
import tech.zakomi.zakdroid.core.search.TextSearch
import tech.zakomi.zakdroid.core.search.TextSearchQuery
import tech.zakomi.zakdroid.core.search.WorkspaceFile
import tech.zakomi.zakdroid.core.search.WorkspaceFiles
import tech.zakomi.zakdroid.state.OpenDocumentsStore
import tech.zakomi.zakdroid.state.WorkspaceSession
import javax.inject.Inject

data class FileMatches(val file: WorkspaceFile, val matches: List<TextMatch>)

data class SearchUiState(
    val query: TextSearchQuery = TextSearchQuery(""),
    val hasWorkspace: Boolean = false,
    val searching: Boolean = false,
    /** Null until a search has run for the current query. */
    val results: List<FileMatches>? = null,
    val matchCount: Int = 0,
    /** True when the result or file limit cut the search short. */
    val truncated: Boolean = false,
    /** The regex engine's message for an invalid pattern. */
    val patternError: String? = null,
    val collapsed: Set<String> = emptySet(),
)

/**
 * "Find in Files" (Ctrl+Shift+F). Unsaved text of open editors is searched as shown,
 * so results match what the user sees. Binary and very large files are skipped.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val fs: FileSystem,
    private val session: WorkspaceSession,
    private val documents: OpenDocumentsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            session.current.collect { ws ->
                job?.cancel()
                _state.update { SearchUiState(query = it.query, hasWorkspace = ws != null) }
                if (ws != null && _state.value.query.text.isNotEmpty()) schedule(0)
            }
        }
    }

    fun setText(text: String) = change { it.copy(text = text) }
    fun toggleRegex() = change { it.copy(regex = !it.regex) }
    fun toggleMatchCase() = change { it.copy(matchCase = !it.matchCase) }
    fun toggleWholeWord() = change { it.copy(wholeWord = !it.wholeWord) }

    /** Runs the search now (the keyboard's search key, or Refresh after files changed). */
    fun searchNow() = schedule(0)

    fun toggleCollapsed(fileKey: String) {
        _state.update { it.copy(collapsed = if (fileKey in it.collapsed) it.collapsed - fileKey else it.collapsed + fileKey) }
    }

    private fun change(transform: (TextSearchQuery) -> TextSearchQuery) {
        _state.update { it.copy(query = transform(it.query)) }
        schedule(DEBOUNCE_MS)
    }

    private fun schedule(delayMs: Long) {
        job?.cancel()
        val query = _state.value.query
        val workspace = session.current.value
        if (workspace == null || query.text.isEmpty()) {
            _state.update { it.copy(searching = false, results = null, matchCount = 0, truncated = false, patternError = null) }
            return
        }
        val pattern = try {
            query.toRegex()
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(searching = false, results = null, matchCount = 0, truncated = false, patternError = e.message?.lineSequence()?.firstOrNull() ?: "") }
            return
        }
        job = viewModelScope.launch {
            delay(delayMs)
            _state.update { it.copy(searching = true, patternError = null, collapsed = emptySet()) }
            // Open editors hold the newest text; read it here, on the main thread.
            val openText = documents.tabs.value.filter { it.dirty }.associate { it.key to it.document.content.toString() }
            val listing = WorkspaceFiles.list(fs, workspace.root, MAX_FILES)
            val results = ArrayList<FileMatches>()
            var total = 0
            var truncated = listing.truncated
            for ((index, file) in listing.files.withIndex()) {
                if (total >= MAX_MATCHES) {
                    truncated = true
                    break
                }
                val text = openText[file.entry.ref.key] ?: readText(file) ?: continue
                val matches = withContext(Dispatchers.Default) {
                    TextSearch.find(text, pattern, minOf(MAX_MATCHES_PER_FILE, MAX_MATCHES - total))
                }
                if (matches.isNotEmpty()) {
                    results += FileMatches(file, matches)
                    total += matches.size
                    if (matches.size >= MAX_MATCHES_PER_FILE) truncated = true
                }
                if (index % PUBLISH_EVERY == PUBLISH_EVERY - 1) {
                    _state.update { it.copy(results = results.toList(), matchCount = total) }
                }
            }
            _state.update { it.copy(searching = false, results = results.toList(), matchCount = total, truncated = truncated) }
        }
    }

    /** The file as text, or null if it is too large, binary or unreadable. */
    private suspend fun readText(file: WorkspaceFile): String? {
        if (file.entry.size > MAX_FILE_BYTES) return null
        val bytes = try {
            fs.read(file.entry.ref, MAX_FILE_BYTES)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        return withContext(Dispatchers.Default) {
            val probe = minOf(bytes.size, 8192)
            if ((0 until probe).any { bytes[it] == 0.toByte() }) null else String(bytes, Charsets.UTF_8)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
        const val MAX_FILES = 5_000
        const val MAX_FILE_BYTES = 1L * 1024 * 1024
        const val MAX_MATCHES = 2_000
        const val MAX_MATCHES_PER_FILE = 200
        const val PUBLISH_EVERY = 50
    }
}
