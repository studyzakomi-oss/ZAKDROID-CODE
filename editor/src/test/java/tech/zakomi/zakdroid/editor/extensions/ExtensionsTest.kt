package tech.zakomi.zakdroid.editor.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val manifest = """
        { "id": "me.demo", "name": "Demo", "version": "1.2.0", "author": "Me", "license": "MIT",
          "contributes": { "snippets": [ { "language": "python", "path": "./snippets/py.json" } ] } }
    """.trimIndent()
    private val snippets = """{ "Hello": { "prefix": "hi", "body": ["print(${'$'}{1:hi})"], "description": "greet" } }"""

    private fun zip(vararg files: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            for ((name, text) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    // ---- Snippet files ----------------------------------------------------------------------

    @Test
    fun parsesStringAndArrayForms() {
        val list = SnippetParser.parse(
            """{ "A": { "prefix": ["a", "aa"], "body": ["x", "y"], "description": "d" },
                 "B": { "prefix": "b", "body": "one" },
                 "NoBody": { "prefix": "n" }, "NotAnObject": 3 }""",
        )
        assertEquals(listOf(Snippet("A", "a", "x\ny", "d"), Snippet("B", "b", "one", "")), list)
    }

    @Test
    fun rejectsNonObjects() {
        assertThrows(IllegalArgumentException::class.java) { SnippetParser.parse("[1, 2]") }
        assertThrows(IllegalArgumentException::class.java) { SnippetParser.parse("{ not json") }
    }

    // ---- Expansion --------------------------------------------------------------------------

    @Test
    fun firstTabStopIsSelectedAndMirrorsRepeatIt() {
        val e = SnippetExpander.expand("for (int \${1:i} = 0; \$1 < \${2:n}; \$1++) {\n    \$0\n}")
        assertEquals("for (int i = 0; i < n; i++) {\n    \n}", e.text)
        assertEquals("i", e.text.substring(e.selectionStart, e.selectionEnd))
        assertEquals(9, e.selectionStart)
    }

    @Test
    fun finalStopIsUsedWhenThereIsNoOther() {
        val e = SnippetExpander.expand("int main(void) {\n    \$0\n    return 0;\n}")
        assertEquals("int main(void) {\n    \n    return 0;\n}", e.text)
        assertEquals(21, e.selectionStart)
        assertEquals(e.selectionStart, e.selectionEnd)
    }

    @Test
    fun cursorGoesToTheEndWithoutStops() {
        val e = SnippetExpander.expand("done")
        assertEquals(ExpandedSnippet("done", 4, 4), e)
    }

    @Test
    fun followingLinesGetTheIndent() {
        val e = SnippetExpander.expand("if x:\n    \${0:pass}", indent = "  ")
        assertEquals("if x:\n      pass", e.text)
        assertEquals("pass", e.text.substring(e.selectionStart, e.selectionEnd))
    }

    @Test
    fun escapesChoicesVariablesAndNesting() {
        assertEquals("cost: \$5 }", SnippetExpander.expand("cost: \\\$5 \\}").text)
        assertEquals("pick a", SnippetExpander.expand("pick \${1|a,b,c|}").text)
        assertEquals("main.c|main|", SnippetExpander.expand("\$TM_FILENAME|\${TM_FILENAME_BASE}|\$UNKNOWN", variables = mapOf("TM_FILENAME" to "main.c", "TM_FILENAME_BASE" to "main")).text)
        assertEquals("fallback", SnippetExpander.expand("\${TM_SELECTED_TEXT:fallback}").text)
        assertEquals("sel", SnippetExpander.expand("\${TM_SELECTED_TEXT:fallback}", variables = mapOf("TM_SELECTED_TEXT" to "sel")).text)
        val nested = SnippetExpander.expand("f(\${1:a, \${2:b}})")
        assertEquals("f(a, b)", nested.text)
        assertEquals("a, b", nested.text.substring(nested.selectionStart, nested.selectionEnd))
        assertEquals("a \$ b \${", SnippetExpander.expand("a \$ b \${").text)
    }

    // ---- Manifest ---------------------------------------------------------------------------

    @Test
    fun manifestIsParsed() {
        val m = ExtensionManifest.parse(manifest)
        assertEquals("me.demo", m.id)
        assertEquals("1.2.0", m.version)
        assertEquals(listOf(SnippetContribution("python", "snippets/py.json")), m.snippets)
    }

    @Test
    fun manifestValidation() {
        fun bad(json: String) = assertThrows(IllegalArgumentException::class.java) { ExtensionManifest.parse(json) }
        bad("""{ "id": "NoDots", "version": "1.0.0" }""")
        bad("""{ "id": "a.b", "version": "1" }""")
        bad("""{ "id": "a.b", "version": "1.0.0", "contributes": { "snippets": [ { "language": "rust", "path": "s.json" } ] } }""")
        bad("""{ "id": "a.b", "version": "1.0.0", "contributes": { "snippets": [ { "language": "c", "path": "../s.json" } ] } }""")
        bad("""{ "id": "a.b", "version": "1.0.0", "contributes": { "snippets": [ { "language": "c", "path": "/etc/passwd" } ] } }""")
        bad("[]")
        assertEquals("a.b", ExtensionManifest.parse("""{ "id": "a.b", "version": "1.0.0" }""").name)
    }

    // ---- Installer --------------------------------------------------------------------------

    @Test
    fun installsFromTheArchiveRootOrOneFolder() {
        val root = tmp.newFolder("ext")
        val m = ExtensionInstaller.install(zip("extension.json" to manifest, "snippets/py.json" to snippets), root)
        assertEquals("me.demo", m.id)
        assertTrue(File(root, "me.demo/snippets/py.json").isFile)

        // Updating from a zip that wraps everything in a folder replaces the old copy.
        ExtensionInstaller.install(zip("demo-1.2.0/extension.json" to manifest, "demo-1.2.0/snippets/py.json" to snippets, "demo-1.2.0/README.md" to "hi"), root)
        assertTrue(File(root, "me.demo/README.md").isFile)
        assertEquals(listOf("me.demo"), root.list()!!.toList())
    }

    @Test
    fun rejectsBadArchivesAndLeavesNothingBehind() {
        val root = tmp.newFolder("ext2")
        fun bad(vararg files: Pair<String, String>) =
            assertThrows(IllegalArgumentException::class.java) { ExtensionInstaller.install(zip(*files), root) }
        bad("readme.txt" to "no manifest")
        bad("extension.json" to manifest) // contributed file missing
        bad("extension.json" to manifest, "snippets/py.json" to "[]") // not a snippet file
        bad("../evil.txt" to "x", "extension.json" to manifest, "snippets/py.json" to snippets)
        assertThrows(IllegalArgumentException::class.java) { ExtensionInstaller.install(ByteArrayInputStream("not a zip".toByteArray()), root) }
        assertEquals(emptyList<String>(), root.list()!!.toList())
        assertFalse(File(root.parentFile, "evil.txt").exists())
    }
}
