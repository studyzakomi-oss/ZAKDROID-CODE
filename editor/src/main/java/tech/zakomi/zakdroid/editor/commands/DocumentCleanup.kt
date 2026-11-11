package tech.zakomi.zakdroid.editor.commands

import io.github.rosemoe.sora.text.Content

/** Save-time clean-ups (VS Code: `files.trimTrailingWhitespace`, `files.insertFinalNewline`). */
object DocumentCleanup {

    /** Removes spaces and tabs at line ends, as one undo step. Returns the number of changed lines. */
    fun trimTrailingWhitespace(content: Content): Int {
        var changed = 0
        content.beginBatchEdit()
        try {
            for (line in 0 until content.lineCount) {
                val length = content.getColumnCount(line)
                if (length == 0) continue
                val text = content.getLine(line)
                var end = length
                while (end > 0 && (text[end - 1] == ' ' || text[end - 1] == '\t')) end--
                if (end < length) {
                    content.delete(line, end, line, length)
                    changed++
                }
            }
        } finally {
            content.endBatchEdit()
        }
        return changed
    }

    /** Makes a non-empty document end with a line break. Returns true if one was added. */
    fun ensureFinalNewline(content: Content): Boolean {
        val last = content.lineCount - 1
        val length = content.getColumnCount(last)
        if (length == 0) return false
        content.insert(last, length, "\n")
        return true
    }
}
