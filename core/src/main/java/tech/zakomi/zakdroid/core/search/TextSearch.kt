package tech.zakomi.zakdroid.core.search

/** What the user typed in a search box, with VS Code's three toggles. */
data class TextSearchQuery(
    val text: String,
    val regex: Boolean = false,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
) {
    /** The compiled pattern. Throws [IllegalArgumentException] for an invalid regular expression. */
    fun toRegex(): Regex {
        val body = if (regex) text else Regex.escape(text)
        val pattern = if (wholeWord) "(?<![\\p{L}\\p{N}_])(?:$body)(?![\\p{L}\\p{N}_])" else body
        val options = buildSet {
            add(RegexOption.MULTILINE)
            if (!matchCase) add(RegexOption.IGNORE_CASE) // Kotlin makes this Unicode-aware on the JVM
        }
        return Regex(pattern, options)
    }
}

/**
 * One match inside a text. [line] and the columns are 0-based and refer to the file;
 * a match never spans lines. [lineText] is the line, or a window of a very long line
 * that starts at column [previewOffset].
 */
data class TextMatch(
    val line: Int,
    val startColumn: Int,
    val endColumn: Int,
    val lineText: String,
    val previewOffset: Int = 0,
)

object TextSearch {
    const val MAX_LINE_PREVIEW = 400

    /**
     * Finds [pattern] line by line (LF, CRLF or CR endings). Stops after [limit] matches.
     * Empty matches (e.g. the regex `a*`) are skipped. Very long lines are cut around the
     * match so a minified file cannot fill the result list with megabytes of text.
     */
    fun find(text: CharSequence, pattern: Regex, limit: Int = Int.MAX_VALUE): List<TextMatch> {
        val out = ArrayList<TextMatch>()
        var line = 0
        var start = 0
        val length = text.length
        while (start <= length && out.size < limit) {
            var end = start
            while (end < length && text[end] != '\n' && text[end] != '\r') end++
            if (end > start) {
                val lineText = text.subSequence(start, end)
                var found = pattern.find(lineText)
                while (found != null && out.size < limit) {
                    val range = found.range
                    if (!range.isEmpty()) out += preview(line, lineText, range.first, range.last + 1)
                    found = if (range.isEmpty()) {
                        if (range.first + 1 > lineText.length) null else pattern.find(lineText, range.first + 1)
                    } else {
                        found.next()
                    }
                }
            }
            if (end >= length) break
            start = if (text[end] == '\r' && end + 1 < length && text[end + 1] == '\n') end + 2 else end + 1
            line++
        }
        return out
    }

    private fun preview(line: Int, lineText: CharSequence, start: Int, end: Int): TextMatch {
        if (lineText.length <= MAX_LINE_PREVIEW) return TextMatch(line, start, end, lineText.toString())
        // Keep the columns pointing at the real file; only the preview text is shortened.
        val from = (start - 40).coerceAtLeast(0)
        val to = (from + MAX_LINE_PREVIEW).coerceAtMost(lineText.length)
        return TextMatch(line, start, end, lineText.subSequence(from, to).toString(), from)
    }
}
