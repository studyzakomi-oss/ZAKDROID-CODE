package tech.zakomi.zakdroid.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    private fun find(text: String, query: TextSearchQuery, limit: Int = Int.MAX_VALUE) =
        TextSearch.find(text, query.toRegex(), limit)

    @Test
    fun plainSearchIsCaseInsensitiveByDefault() {
        val hits = find("int Main;\nmain();\n", TextSearchQuery("main"))
        assertEquals(listOf(0 to 4, 1 to 0), hits.map { it.line to it.startColumn })
        assertEquals("main();", hits[1].lineText)
        assertEquals(4, hits[1].endColumn)
    }

    @Test
    fun matchCase() {
        assertEquals(1, find("Main main", TextSearchQuery("main", matchCase = true)).size)
    }

    @Test
    fun plainTextIsNotARegex() {
        assertEquals(1, find("a.b axb", TextSearchQuery("a.b")).size)
        assertEquals(1, find("f(x) fx", TextSearchQuery("f(x)")).size)
    }

    @Test
    fun wholeWord() {
        val hits = find("int i; print(i); idx = i2;", TextSearchQuery("i", wholeWord = true))
        assertEquals(listOf(4, 13), hits.map { it.startColumn })
    }

    @Test
    fun regexWithGroupsAndWholeWord() {
        val hits = find("cat cats dog", TextSearchQuery("cat|dog", regex = true, wholeWord = true))
        assertEquals(listOf(0, 9), hits.map { it.startColumn })
    }

    @Test
    fun invalidRegexThrows() {
        assertThrows(IllegalArgumentException::class.java) { TextSearchQuery("(", regex = true).toRegex() }
    }

    @Test
    fun emptyMatchesAreSkipped() {
        assertEquals(listOf(1), find("baab", TextSearchQuery("a*", regex = true)).map { it.startColumn })
    }

    @Test
    fun lineEndings() {
        val hits = find("x\r\nx\rx\nx", TextSearchQuery("x"))
        assertEquals(listOf(0, 1, 2, 3), hits.map { it.line })
        assertTrue(hits.all { it.lineText == "x" })
    }

    @Test
    fun anchorsApplyPerLine() {
        assertEquals(listOf(0, 1), find("ab\nab", TextSearchQuery("^a", regex = true)).map { it.line })
    }

    @Test
    fun limitStopsEarly() {
        assertEquals(3, find("a a a a a\na a", TextSearchQuery("a"), limit = 3).size)
    }

    @Test
    fun longLinesAreCutAroundTheMatch() {
        val line = "x".repeat(5000) + "needle" + "y".repeat(5000)
        val hit = find(line, TextSearchQuery("needle")).single()
        assertEquals(5000, hit.startColumn)
        assertEquals(TextSearch.MAX_LINE_PREVIEW, hit.lineText.length)
        assertEquals("needle", hit.lineText.substring(hit.startColumn - hit.previewOffset, hit.endColumn - hit.previewOffset))
    }
}
