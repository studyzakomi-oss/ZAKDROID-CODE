package tech.zakomi.zakdroid.editor.ui

import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CursorPosition(val line: Int = 1, val column: Int = 1)

data class SearchState(
    val active: Boolean = false,
    val query: String = "",
    val matchCount: Int = 0,
    /** 1-based index of the selected match, or 0 when the cursor is not on a match. */
    val currentMatch: Int = 0,
    val error: String? = null,
)

data class SearchQuery(
    val text: String,
    val regex: Boolean = false,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
)

/** Commands from the workbench (toolbar, keyboard shortcuts) to the visible editor. */
class EditorController {
    private var editor: CodeEditor? = null

    private val _cursor = MutableStateFlow(CursorPosition())
    val cursor: StateFlow<CursorPosition> = _cursor.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    internal fun attach(view: CodeEditor) {
        editor = view
        refreshHistory()
    }

    internal fun detach(view: CodeEditor) {
        if (editor === view) editor = null
    }

    internal fun onCursorMoved(line: Int, column: Int) {
        _cursor.value = CursorPosition(line + 1, column + 1)
        refreshSearchIndex()
    }

    internal fun refreshHistory() {
        val text = editor?.text
        _canUndo.value = text?.canUndo() == true
        _canRedo.value = text?.canRedo() == true
    }

    internal fun onSearchResultPublished() {
        val searcher = editor?.searcher ?: return
        _search.value = _search.value.copy(
            matchCount = if (searcher.hasQuery()) searcher.matchedPositionCount else 0,
            currentMatch = if (searcher.hasQuery()) searcher.currentMatchedPositionIndex + 1 else 0,
        )
    }

    private fun refreshSearchIndex() {
        if (_search.value.active) onSearchResultPublished()
    }

    fun undo() {
        editor?.undo()
        refreshHistory()
    }

    fun redo() {
        editor?.redo()
        refreshHistory()
    }

    fun openSearch() {
        val selected = editor?.let { e ->
            val c = e.cursor
            if (c.isSelected && c.leftLine == c.rightLine) e.text.subContent(c.leftLine, c.leftColumn, c.rightLine, c.rightColumn).toString() else null
        }
        _search.value = _search.value.copy(active = true, query = selected ?: _search.value.query)
    }

    fun search(query: SearchQuery) {
        val e = editor ?: return
        if (query.text.isEmpty()) {
            e.searcher.stopSearch()
            _search.value = _search.value.copy(query = "", matchCount = 0, currentMatch = 0, error = null)
            return
        }
        val type = when {
            query.regex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
            query.wholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
            else -> EditorSearcher.SearchOptions.TYPE_NORMAL
        }
        try {
            if (query.regex) Regex(query.text)
            e.searcher.search(query.text, EditorSearcher.SearchOptions(type, !query.matchCase))
            _search.value = _search.value.copy(query = query.text, error = null)
        } catch (ex: IllegalArgumentException) {
            e.searcher.stopSearch()
            _search.value = _search.value.copy(query = query.text, matchCount = 0, currentMatch = 0, error = ex.message ?: "Invalid pattern")
        }
    }

    fun next() {
        editor?.searcher?.takeIf { it.hasQuery() }?.gotoNext()
        refreshSearchIndex()
    }

    fun previous() {
        editor?.searcher?.takeIf { it.hasQuery() }?.gotoPrevious()
        refreshSearchIndex()
    }

    fun replace(replacement: String) {
        val searcher = editor?.searcher?.takeIf { it.hasQuery() } ?: return
        if (searcher.isMatchedPositionSelected) searcher.replaceCurrentMatch(replacement) else searcher.gotoNext()
        refreshHistory()
    }

    fun replaceAll(replacement: String) {
        editor?.searcher?.takeIf { it.hasQuery() }?.replaceAll(replacement) { refreshHistory() }
    }

    fun closeSearch() {
        editor?.searcher?.stopSearch()
        _search.value = SearchState()
    }

    fun gotoLine(line: Int) {
        val e = editor ?: return
        val target = (line - 1).coerceIn(0, e.lineCount - 1)
        e.setSelection(target, 0, true)
    }

    fun requestFocus() {
        editor?.requestFocus()
    }
}
