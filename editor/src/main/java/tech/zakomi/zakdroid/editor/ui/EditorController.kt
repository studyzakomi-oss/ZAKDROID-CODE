package tech.zakomi.zakdroid.editor.ui

import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.zakomi.zakdroid.editor.commands.LineCommands
import tech.zakomi.zakdroid.editor.commands.TextSelection
import tech.zakomi.zakdroid.editor.document.EditorDocument
import tech.zakomi.zakdroid.editor.extensions.Snippet
import tech.zakomi.zakdroid.editor.extensions.SnippetExpander

/** 1-based cursor position (left end of a selection) and the number of selected characters. */
data class CursorPosition(val line: Int = 1, val column: Int = 1, val selected: Int = 0)

/** Line commands of the editor; see [LineCommands]. */
enum class LineCommand { ToggleComment, DuplicateDown, DuplicateUp, MoveUp, MoveDown, Delete }

data class SearchState(
    val active: Boolean = false,
    val query: String = "",
    val showReplace: Boolean = false,
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

    private class Reveal(val document: EditorDocument, val line: Int, val startColumn: Int, val endColumn: Int)

    private var pendingReveal: Reveal? = null

    internal fun onCursorMoved(line: Int, column: Int, selected: Int = 0) {
        _cursor.value = CursorPosition(line + 1, column + 1, selected.coerceAtLeast(0))
        refreshSearchIndex()
    }

    /** Called when the editor view starts showing [document]: a reveal requested before that runs now. */
    internal fun onDocumentShown(document: EditorDocument) {
        val reveal = pendingReveal ?: return
        pendingReveal = null
        if (reveal.document === document) editor?.let { e -> e.post { if (e.text === document.content) apply(e, reveal) } }
    }

    /**
     * Moves the cursor to a 0-based position in [document] and selects up to [endColumn]
     * (Quick Open, search results, symbols). If the editor does not show the document
     * yet, the jump happens as soon as it does.
     */
    fun reveal(document: EditorDocument, line: Int, startColumn: Int = 0, endColumn: Int = startColumn) {
        val reveal = Reveal(document, line, startColumn, endColumn)
        val e = editor
        if (e != null && e.text === document.content) apply(e, reveal) else pendingReveal = reveal
    }

    private fun apply(e: CodeEditor, reveal: Reveal) {
        val line = reveal.line.coerceIn(0, e.lineCount - 1)
        val length = e.text.getColumnCount(line)
        val start = reveal.startColumn.coerceIn(0, length)
        val end = reveal.endColumn.coerceIn(start, length)
        if (end > start) e.setSelectionRegion(line, start, line, end) else e.setSelection(line, start)
        e.requestFocus()
    }

    /** True while the code editor (not the terminal or a text field) has input focus. */
    val hasFocus: Boolean get() = editor?.hasFocus() == true

    val lineCount: Int get() = editor?.lineCount ?: 0

    fun selectAll() {
        editor?.selectAll()
    }

    /** Inserts [snippet] at the cursor (replacing a selection) and selects its first tab stop. */
    fun insertSnippet(snippet: Snippet, fileName: String): Boolean {
        val e = editor ?: return false
        if (!e.isEditable) return false
        val text = e.text
        val c = e.cursor
        val line = text.getLineString(c.leftLine)
        val indent = line.take(c.leftColumn).takeWhile { it == ' ' || it == '\t' }
        val selected = if (c.isSelected) text.subContent(c.leftLine, c.leftColumn, c.rightLine, c.rightColumn).toString() else ""
        val expanded = SnippetExpander.expand(
            snippet.body,
            indent,
            mapOf(
                "TM_FILENAME" to fileName,
                "TM_FILENAME_BASE" to fileName.substringBeforeLast('.'),
                "TM_SELECTED_TEXT" to selected,
            ),
        )
        val start = c.left
        text.replace(c.leftLine, c.leftColumn, c.rightLine, c.rightColumn, expanded.text)
        val from = text.indexer.getCharPosition(start + expanded.selectionStart)
        val to = text.indexer.getCharPosition(start + expanded.selectionEnd)
        if (expanded.selectionEnd > expanded.selectionStart) {
            e.setSelectionRegion(from.line, from.column, to.line, to.column)
        } else {
            e.setSelection(from.line, from.column)
        }
        e.requestFocus()
        refreshHistory()
        return true
    }

    /** Runs a line command on the current selection. [lineComment] is the language's comment prefix. */
    fun run(command: LineCommand, lineComment: String? = null): Boolean {
        val e = editor ?: return false
        if (!e.isEditable) return false
        val c = e.cursor
        val selection = TextSelection(c.leftLine, c.leftColumn, c.rightLine, c.rightColumn)
        val text = e.text
        val result = when (command) {
            LineCommand.ToggleComment -> LineCommands.toggleLineComment(text, selection, lineComment ?: return false)
            LineCommand.DuplicateDown -> LineCommands.duplicateLines(text, selection, selectCopy = true)
            LineCommand.DuplicateUp -> LineCommands.duplicateLines(text, selection, selectCopy = false)
            LineCommand.MoveUp -> LineCommands.moveLines(text, selection, up = true)
            LineCommand.MoveDown -> LineCommands.moveLines(text, selection, up = false)
            LineCommand.Delete -> LineCommands.deleteLines(text, selection)
        } ?: return false
        if (result.isEmpty) {
            e.setSelection(result.startLine, result.startColumn)
        } else {
            e.setSelectionRegion(result.startLine, result.startColumn, result.endLine, result.endColumn)
        }
        refreshHistory()
        return true
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

    /** The selected text if it lies on one line (the usual seed for a search box), else null. */
    fun selectedTextOnOneLine(): String? = editor?.let { e ->
        val c = e.cursor
        if (c.isSelected && c.leftLine == c.rightLine) e.text.subContent(c.leftLine, c.leftColumn, c.rightLine, c.rightColumn).toString() else null
    }

    /** Opens the find widget; [withReplace] also shows the replace row (Ctrl+H). */
    fun openSearch(withReplace: Boolean = false) {
        val current = _search.value
        _search.value = current.copy(
            active = true,
            query = selectedTextOnOneLine() ?: current.query,
            showReplace = withReplace || (current.active && current.showReplace),
        )
    }

    fun toggleReplace() {
        _search.value = _search.value.copy(showReplace = !_search.value.showReplace)
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

    /** Jumps to a 1-based line and column, like VS Code's "Go to Line" (`12` or `12:5`). */
    fun gotoLine(line: Int, column: Int = 1) {
        val e = editor ?: return
        val target = (line - 1).coerceIn(0, e.lineCount - 1)
        e.setSelection(target, (column - 1).coerceIn(0, e.text.getColumnCount(target)), true)
        e.requestFocus()
    }

    fun requestFocus() {
        editor?.requestFocus()
    }
}
