package tech.zakomi.zakdroid.editor.extensions

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** One snippet in VS Code's format: typed [prefix], inserted [body] with `$1`-style tab stops. */
data class Snippet(val name: String, val prefix: String, val body: String, val description: String)

/** A snippet body made ready for insertion; the selection is the first tab stop. */
data class ExpandedSnippet(val text: String, val selectionStart: Int, val selectionEnd: Int)

object SnippetParser {
    /**
     * Parses a VS Code snippet file: `{ "Name": { "prefix": "..", "body": [".."], "description": ".." } }`.
     * `prefix` and `body` may be a string or an array (lines). Entries without a body are skipped.
     * Throws [IllegalArgumentException] if the text is not a JSON object.
     */
    fun parse(json: String): List<Snippet> {
        val root = try {
            JsonParser.parseString(json)
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("Not valid JSON: ${e.message}", e)
        }
        require(root.isJsonObject) { "A snippet file must be a JSON object" }
        return root.asJsonObject.entrySet().mapNotNull { (name, value) ->
            if (!value.isJsonObject) return@mapNotNull null
            val entry = value.asJsonObject
            val body = text(entry.get("body"), "\n") ?: return@mapNotNull null
            val prefix = entry.get("prefix")?.let { if (it.isJsonArray) first(it.asJsonArray) else string(it) }.orEmpty()
            Snippet(name, prefix, body, string(entry.get("description")).orEmpty())
        }
    }

    private fun string(e: JsonElement?): String? = if (e != null && e.isJsonPrimitive) e.asString else null
    private fun first(a: JsonArray): String? = a.firstOrNull()?.let(::string)
    private fun text(e: JsonElement?, separator: String): String? = when {
        e == null -> null
        e.isJsonArray -> e.asJsonArray.mapNotNull(::string).joinToString(separator)
        else -> string(e)
    }
}

/**
 * Turns a snippet body into plain text. Tab stops (`$1`, `${1:default}`, `${1|a,b|}`) become their
 * default text, the first one is selected, and `$0` (or the end) is where the cursor goes when
 * there is none. Known variables are filled in; unknown ones become their default or nothing.
 * Tab-key navigation between stops is not supported.
 */
object SnippetExpander {

    fun expand(body: String, indent: String = "", variables: Map<String, String> = emptyMap()): ExpandedSnippet {
        val out = StringBuilder()
        val stops = HashMap<Int, IntRange>() // tab stop -> range in out (end exclusive via last+1)
        Parser(body.replace("\r\n", "\n"), variables, out, stops).run()
        // Lines after the first keep the indentation of the line the snippet is inserted on.
        val text = out.toString()
        fun shifted(offset: Int) = offset + indent.length * text.substring(0, offset).count { it == '\n' }
        val indented = if (indent.isEmpty()) text else text.replace("\n", "\n" + indent)
        val first = stops.keys.filter { it > 0 }.minOrNull() ?: stops.keys.firstOrNull()
        val range = first?.let { stops.getValue(it) }
        return if (range == null) {
            ExpandedSnippet(indented, indented.length, indented.length)
        } else {
            ExpandedSnippet(indented, shifted(range.first), shifted(range.last + 1))
        }
    }

    private class Parser(
        private val src: String,
        private val variables: Map<String, String>,
        private val out: StringBuilder,
        private val stops: HashMap<Int, IntRange>,
    ) {
        private var i = 0

        fun run() = parse(insideBraces = false)

        private fun parse(insideBraces: Boolean) {
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '\\' && i + 1 < src.length && src[i + 1] in "\$}\\" -> { out.append(src[i + 1]); i += 2 }
                    c == '}' && insideBraces -> return
                    c == '$' -> dollar()
                    else -> { out.append(c); i++ }
                }
            }
        }

        private fun dollar() {
            i++ // '$'
            if (i >= src.length) { out.append('$'); return }
            when {
                src[i].isDigit() -> {
                    // A bare repeat of an earlier stop mirrors that stop's default text.
                    val number = readNumber()
                    val from = out.length
                    out.append(defaults[number].orEmpty())
                    mark(number, from, out.length)
                }
                isNameStart(src[i]) -> out.append(variables[readName()].orEmpty())
                src[i] == '{' -> braces()
                else -> out.append('$')
            }
        }

        private fun braces() {
            i++ // '{'
            val isStop = i < src.length && src[i].isDigit()
            val isVariable = i < src.length && isNameStart(src[i])
            if (!isStop && !isVariable) { out.append("\${"); return }
            val number = if (isStop) readNumber() else -1
            val name = if (isVariable) readName() else ""
            val from = out.length
            when (src.getOrNull(i)) {
                '}' -> { i++; if (isVariable) out.append(variables[name].orEmpty()) }
                ':' -> {
                    i++
                    val known = if (isVariable) variables[name] else null
                    val before = out.length
                    parse(insideBraces = true)
                    if (!known.isNullOrEmpty()) { out.setLength(before); out.append(known) }
                    if (src.getOrNull(i) == '}') i++
                }
                '|' -> { // choice: take the first option
                    i++
                    val end = src.indexOf("|}", i).takeIf { it >= 0 } ?: src.length
                    out.append(src.substring(i, end).split(',').firstOrNull().orEmpty())
                    i = minOf(end + 2, src.length)
                }
                else -> { // transforms (${1/regex/format/}) are not supported: drop them
                    val end = src.indexOf('}', i).takeIf { it >= 0 } ?: (src.length - 1)
                    i = end + 1
                    if (isVariable) out.append(variables[name].orEmpty())
                }
            }
            if (isStop) mark(number, from, out.length)
        }

        private val defaults = HashMap<Int, String>()

        private fun mark(number: Int, from: Int, to: Int) {
            // The first occurrence of a tab stop wins (later ones are mirrors).
            if (number !in stops) stops[number] = from until to
            if (to > from && number !in defaults) defaults[number] = out.substring(from, to)
        }

        private fun readNumber(): Int {
            val s = i
            while (i < src.length && src[i].isDigit()) i++
            return src.substring(s, i).toIntOrNull() ?: 0
        }

        private fun readName(): String {
            val s = i
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
            return src.substring(s, i)
        }

        private fun isNameStart(c: Char) = c.isLetter() || c == '_'
    }
}
