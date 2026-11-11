package tech.zakomi.zakdroid.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    @Test
    fun subsequenceIsRequired() {
        assertNotNull(FuzzyMatcher.match("mc", "main.c"))
        assertNull(FuzzyMatcher.match("cm", "main.c"))
        assertNull(FuzzyMatcher.match("mainx", "main.c"))
        assertNull(FuzzyMatcher.match("longer than text", "a"))
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertEquals(FuzzyMatch(0, emptyList()), FuzzyMatcher.match("", "main.c"))
        assertEquals(FuzzyMatch(0, emptyList()), FuzzyMatcher.match("  ", "main.c"))
    }

    @Test
    fun matchingIgnoresCaseAndSpaces() {
        assertEquals(listOf(0, 1, 2, 3), FuzzyMatcher.match("MAIN", "main.c")!!.positions)
        assertEquals(listOf(0, 6), FuzzyMatcher.match("e v", "EditorViewModel")!!.positions)
    }

    @Test
    fun positionsPreferWordStarts() {
        // "evm" should pick the capitals, not the first e-v-m in order.
        assertEquals(listOf(0, 6, 10), FuzzyMatcher.match("evm", "EditorViewModel")!!.positions)
    }

    @Test
    fun consecutiveBeatsScattered() {
        val together = FuzzyMatcher.match("main", "main.c")!!.score
        val scattered = FuzzyMatcher.match("main", "m_a_i_n.c")!!.score
        assertTrue(together > scattered)
    }

    @Test
    fun fileNameHitBeatsFolderHit() {
        val inName = FuzzyMatcher.matchPath("util", "src/deep/folder/util.c")!!
        val inFolder = FuzzyMatcher.matchPath("util", "util/main.c")!!
        assertTrue(inName.score > inFolder.score)
        // Positions refer to the whole path.
        assertEquals(listOf(16, 17, 18, 19), inName.positions)
    }

    @Test
    fun queryWithSlashMatchesTheWholePath() {
        assertNotNull(FuzzyMatcher.matchPath("src/ma", "src/main.c"))
        assertNull(FuzzyMatcher.matchPath("lib/ma", "src/main.c"))
    }

    @Test
    fun rankingPutsTheBestFileFirst() {
        val files = listOf("docs/readme_main.md", "src/main.c", "src/domain.c", "Makefile")
        val ranked = files.mapNotNull { f -> FuzzyMatcher.matchPath("main", f)?.let { f to it.score } }
            .sortedByDescending { it.second }.map { it.first }
        assertEquals("src/main.c", ranked.first())
        assertTrue("Makefile" !in ranked)
    }
}
