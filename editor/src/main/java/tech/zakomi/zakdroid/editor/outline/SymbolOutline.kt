package tech.zakomi.zakdroid.editor.outline

import tech.zakomi.zakdroid.editor.EditorLanguage

enum class SymbolKind { Function, Class, Struct, Enum, Namespace, Macro }

/** A definition found in a file. [line] is 0-based; [depth] is the nesting level for display. */
data class DocumentSymbol(val name: String, val kind: SymbolKind, val line: Int, val depth: Int)

/**
 * "Go to Symbol in File" without a language server: definitions are found by
 * line patterns, so unusual formatting can be missed. clangd and pylsp replace
 * this in milestone M1.
 */
object SymbolOutline {

    fun of(language: EditorLanguage, text: CharSequence): List<DocumentSymbol> = when (language) {
        EditorLanguage.Python -> python(text)
        EditorLanguage.C, EditorLanguage.Cpp -> cFamily(text)
        EditorLanguage.PlainText -> emptyList()
    }

    // ---- Python -----------------------------------------------------------------------------

    private val PY_DEF = Regex("""^([ \t]*)(?:async[ \t]+)?(def|class)[ \t]+([A-Za-z_][A-Za-z0-9_]*)""")

    private fun python(text: CharSequence): List<DocumentSymbol> {
        val out = ArrayList<DocumentSymbol>()
        val indents = ArrayList<Int>() // indentation of the enclosing definitions
        text.lineSequence().forEachIndexed { index, line ->
            val m = PY_DEF.find(line) ?: return@forEachIndexed
            val indent = m.groupValues[1].fold(0) { sum, c -> sum + if (c == '\t') 8 else 1 }
            while (indents.isNotEmpty() && indents.last() >= indent) indents.removeAt(indents.size - 1)
            out += DocumentSymbol(m.groupValues[3], if (m.groupValues[2] == "class") SymbolKind.Class else SymbolKind.Function, index, indents.size)
            indents += indent
        }
        return out
    }

    // ---- C and C++ --------------------------------------------------------------------------

    private val C_MACRO = Regex("""^\s*#\s*define\s+([A-Za-z_]\w*)""")
    private val C_TYPE = Regex(
        """^\s*(?:typedef\s+|export\s+)?(struct|class|union|enum(?:\s+(?:class|struct))?|namespace)\s+([A-Za-z_][\w:]*)\s*(?:final\s*)?(?::[^;{]*)?(?:\{.*)?$""",
    )
    private val C_FUNCTION = Regex(
        """^\s*([A-Za-z_][\w\s*&:<>,\[\]]*?[\s*&>])??((?:[A-Za-z_]\w*::)*~?[A-Za-z_]\w*|(?:[A-Za-z_]\w*::)*operator\s*[^\s(]+)\s*\([^;{}]*(?:\)|,)\s*(?:const|noexcept|override|final|\s)*(?:\{[^;]*)?$""",
    )
    private val NOT_FUNCTIONS = setOf(
        "if", "for", "while", "switch", "return", "sizeof", "catch", "else", "do", "case", "new", "delete", "throw",
        "defined", "alignof", "decltype", "static_assert", "typeof",
    )
    private val NOT_RETURN_TYPES = setOf("return", "else", "case", "new", "delete", "throw", "goto", "typedef", "using", "co_return", "co_yield")

    private fun cFamily(text: CharSequence): List<DocumentSymbol> {
        val out = ArrayList<DocumentSymbol>()
        var inBlockComment = false
        var depth = 0
        text.lineSequence().forEachIndexed { index, raw ->
            val (line, stillInComment) = stripComments(raw, inBlockComment)
            inBlockComment = stillInComment
            if (line.isBlank()) return@forEachIndexed
            val depthBefore = depth
            depth = (depth + line.count { it == '{' } - line.count { it == '}' }).coerceAtLeast(0)

            C_MACRO.find(line)?.let {
                out += DocumentSymbol(it.groupValues[1], SymbolKind.Macro, index, 0)
                return@forEachIndexed
            }
            if (line.trimStart().startsWith("#")) return@forEachIndexed
            C_TYPE.find(line)?.let {
                val kind = when {
                    it.groupValues[1].startsWith("enum") -> SymbolKind.Enum
                    it.groupValues[1] == "class" -> SymbolKind.Class
                    it.groupValues[1] == "namespace" -> SymbolKind.Namespace
                    else -> SymbolKind.Struct
                }
                out += DocumentSymbol(it.groupValues[2], kind, index, depthBefore.coerceAtMost(MAX_DEPTH))
                return@forEachIndexed
            }
            val m = C_FUNCTION.find(line) ?: return@forEachIndexed
            val returnType = m.groupValues[1].trim()
            val name = m.groupValues[2]
            val plainName = name.substringAfterLast("::")
            if (plainName in NOT_FUNCTIONS) return@forEachIndexed
            if (returnType.substringBefore(' ') in NOT_RETURN_TYPES) return@forEachIndexed
            // A call such as `foo(a,` has no return type; only `A::A(...)` style definitions may omit it.
            if (returnType.isEmpty() && !name.contains("::")) return@forEachIndexed
            out += DocumentSymbol(name, SymbolKind.Function, index, depthBefore.coerceAtMost(MAX_DEPTH))
        }
        return out
    }

    /** Removes `//` and block comments and the contents of string literals from one line. */
    private fun stripComments(line: String, startsInComment: Boolean): Pair<String, Boolean> {
        val sb = StringBuilder(line.length)
        var inComment = startsInComment
        var quote = 0.toChar()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            val next = if (i + 1 < line.length) line[i + 1] else 0.toChar()
            when {
                inComment -> if (c == '*' && next == '/') { inComment = false; i++ }
                quote != 0.toChar() -> when {
                    c == '\\' -> i++
                    c == quote -> { quote = 0.toChar(); sb.append(c) }
                }
                c == '/' && next == '/' -> return sb.toString() to false
                c == '/' && next == '*' -> { inComment = true; i++ }
                c == '"' || c == '\'' -> { quote = c; sb.append(c) }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString() to inComment
    }

    private const val MAX_DEPTH = 4
}
