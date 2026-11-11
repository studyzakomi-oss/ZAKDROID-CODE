package tech.zakomi.zakdroid.core.search

/** A fuzzy hit: higher [score] is better; [positions] are the matched character indices in the text. */
data class FuzzyMatch(val score: Int, val positions: List<Int>)

/**
 * Subsequence matching in the style of VS Code's Quick Open: every character of the
 * query must appear in the text, in order. Consecutive characters, word starts and
 * exact case score higher, so "edvm" finds "EditorViewModel.kt" before "kedavim.txt".
 */
object FuzzyMatcher {
    private const val MATCH = 16
    private const val CONSECUTIVE = 24
    private const val WORD_START = 20
    private const val FIRST_CHAR = 12
    private const val SAME_CASE = 2
    private const val GAP = 1

    /** Returns null when [query] is not a subsequence of [text] (case-insensitive). Spaces in the query are ignored. */
    fun match(query: String, text: String): FuzzyMatch? {
        val q = query.filterNot { it.isWhitespace() }
        if (q.isEmpty()) return FuzzyMatch(0, emptyList())
        if (q.length > text.length) return null
        // best[j] = best score of matching q[0..i] with q[i] placed exactly at text[j]
        val n = text.length
        var previous = IntArray(n) { NONE }
        val from = Array(q.length) { IntArray(n) { -1 } }
        for (i in q.indices) {
            val current = IntArray(n) { NONE }
            var bestBefore = NONE // best of previous[0..j-2], with the gap penalty applied
            var bestBeforeAt = -1
            for (j in i until n) {
                if (i > 0 && j >= 2 && previous[j - 2] != NONE) {
                    val candidate = previous[j - 2]
                    if (bestBefore == NONE || candidate > bestBefore) {
                        bestBefore = candidate
                        bestBeforeAt = j - 2
                    }
                }
                if (bestBefore != NONE) bestBefore -= GAP
                if (!text[j].equals(q[i], ignoreCase = true)) continue
                var bonus = MATCH
                if (text[j] == q[i]) bonus += SAME_CASE
                if (isWordStart(text, j)) bonus += WORD_START
                if (j == 0) bonus += FIRST_CHAR
                if (i == 0) {
                    current[j] = bonus - minOf(j, 8) * GAP
                    continue
                }
                var best = NONE
                var bestAt = -1
                if (previous[j - 1] != NONE) {
                    best = previous[j - 1] + CONSECUTIVE
                    bestAt = j - 1
                }
                if (bestBefore != NONE && bestBefore > best) {
                    best = bestBefore
                    bestAt = bestBeforeAt
                }
                if (best != NONE) {
                    current[j] = best + bonus
                    from[i][j] = bestAt
                }
            }
            previous = current
        }
        var end = -1
        for (j in 0 until n) if (previous[j] != NONE && (end < 0 || previous[j] > previous[end])) end = j
        if (end < 0) return null
        val positions = IntArray(q.length)
        var j = end
        for (i in q.indices.reversed()) {
            positions[i] = j
            j = from[i][j]
        }
        return FuzzyMatch(previous[end], positions.toList())
    }

    /**
     * Scores a file for Quick Open. A hit in the file name beats a hit that needs the
     * folder part of [relativePath]; positions always refer to [relativePath].
     */
    fun matchPath(query: String, relativePath: String): FuzzyMatch? {
        val nameStart = relativePath.lastIndexOf('/') + 1
        if (!query.contains('/')) {
            match(query, relativePath.substring(nameStart))?.let { hit ->
                return FuzzyMatch(hit.score + NAME_BONUS - relativePath.length / 8, hit.positions.map { it + nameStart })
            }
        }
        return match(query, relativePath)?.let { it.copy(score = it.score - relativePath.length / 8) }
    }

    private fun isWordStart(text: String, index: Int): Boolean {
        if (index == 0) return true
        val before = text[index - 1]
        val here = text[index]
        return !before.isLetterOrDigit() || (before.isLowerCase() && here.isUpperCase())
    }

    private const val NONE = Int.MIN_VALUE
    private const val NAME_BONUS = 1000
}
