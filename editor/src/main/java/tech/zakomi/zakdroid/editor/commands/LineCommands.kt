package tech.zakomi.zakdroid.editor.commands

import io.github.rosemoe.sora.text.Content

/** A selection in a document, 0-based. [startLine]/[startColumn] is the left end. */
data class TextSelection(val startLine: Int, val startColumn: Int, val endLine: Int, val endColumn: Int) {
    val isEmpty: Boolean get() = startLine == endLine && startColumn == endColumn

    /** Last line the selection really covers: a selection that ends at column 0 does not include that line. */
    val lastCoveredLine: Int get() = if (endLine > startLine && endColumn == 0) endLine - 1 else endLine
}

/**
 * VS Code's line commands (toggle comment, duplicate, move, delete) on a sora [Content].
 * Each command is one undo step and returns the selection to show afterwards, or null
 * when nothing changed.
 */
object LineCommands {

    /** Ctrl+/ : comments the covered lines with [token], or uncomments them if all are commented. */
    fun toggleLineComment(content: Content, selection: TextSelection, token: String): TextSelection? {
        val lines = (selection.startLine..selection.lastCoveredLine).toList()
        val nonBlank = lines.filter { content.getLineString(it).isNotBlank() }
        val targets = nonBlank.ifEmpty { lines }
        val indents = targets.associateWith { indentOf(content.getLineString(it)) }
        val allCommented = nonBlank.isNotEmpty() && nonBlank.all { content.getLineString(it).startsWith(token, indents.getValue(it)) }

        return batch(content) {
            if (allCommented) {
                val removed = HashMap<Int, Pair<Int, Int>>() // line -> (column, length)
                for (line in targets) {
                    val text = content.getLineString(line)
                    val at = indents.getValue(line)
                    val length = token.length + if (text.startsWith(" ", at + token.length)) 1 else 0
                    content.delete(line, at, line, at + length)
                    removed[line] = at to length
                }
                fun shift(line: Int, column: Int): Int {
                    val (at, length) = removed[line] ?: return column
                    return when {
                        column >= at + length -> column - length
                        column > at -> at
                        else -> column
                    }
                }
                selection.copy(startColumn = shift(selection.startLine, selection.startColumn), endColumn = shift(selection.endLine, selection.endColumn))
            } else {
                val at = targets.minOf { indents.getValue(it) }
                val prefix = "$token "
                for (line in targets) content.insert(line, at, prefix)
                fun shift(line: Int, column: Int) = if (line in targets && column >= at) column + prefix.length else column
                selection.copy(startColumn = shift(selection.startLine, selection.startColumn), endColumn = shift(selection.endLine, selection.endColumn))
            }
        }
    }

    /** Shift+Alt+Down / Up: copies the covered lines below. With [selectCopy] the selection moves to the copy. */
    fun duplicateLines(content: Content, selection: TextSelection, selectCopy: Boolean): TextSelection {
        val first = selection.startLine
        val last = selection.lastCoveredLine
        val block = (first..last).joinToString("\n") { content.getLineString(it) }
        return batch(content) {
            content.insert(last, content.getColumnCount(last), "\n" + block)
            if (selectCopy) selection.movedBy(last - first + 1) else selection
        }
    }

    /** Alt+Up / Alt+Down: moves the covered lines one line up or down. Null at the document edge. */
    fun moveLines(content: Content, selection: TextSelection, up: Boolean): TextSelection? {
        val first = selection.startLine
        val last = selection.lastCoveredLine
        if (up && first == 0) return null
        if (!up && last >= content.lineCount - 1) return null
        return batch(content) {
            if (up) {
                val above = content.getLineString(first - 1)
                content.delete(first - 1, 0, first, 0)
                content.insert(last - 1, content.getColumnCount(last - 1), "\n" + above)
                selection.movedBy(-1)
            } else {
                val below = content.getLineString(last + 1)
                content.delete(last, content.getColumnCount(last), last + 1, content.getColumnCount(last + 1))
                content.insert(first, 0, below + "\n")
                selection.movedBy(1)
            }
        }
    }

    /** Ctrl+Shift+K: deletes the covered lines; the cursor stays in its column where possible. */
    fun deleteLines(content: Content, selection: TextSelection): TextSelection {
        val first = selection.startLine
        val last = selection.lastCoveredLine
        return batch(content) {
            val line = when {
                last < content.lineCount - 1 -> {
                    content.delete(first, 0, last + 1, 0)
                    first
                }
                first > 0 -> {
                    content.delete(first - 1, content.getColumnCount(first - 1), last, content.getColumnCount(last))
                    first - 1
                }
                else -> {
                    content.delete(0, 0, last, content.getColumnCount(last))
                    0
                }
            }
            val column = selection.startColumn.coerceAtMost(content.getColumnCount(line))
            TextSelection(line, column, line, column)
        }
    }

    private fun TextSelection.movedBy(lines: Int) = copy(startLine = startLine + lines, endLine = endLine + lines)

    private fun indentOf(line: String): Int {
        var i = 0
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
        return i
    }

    private inline fun <T> batch(content: Content, edit: () -> T): T {
        content.beginBatchEdit()
        try {
            return edit()
        } finally {
            content.endBatchEdit()
        }
    }
}
