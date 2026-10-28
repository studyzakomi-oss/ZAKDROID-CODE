package tech.zakomi.zakdroid.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStepTest {
    @Test
    fun `weights add up to one hundred`() {
        assertEquals(100, InstallStep.entries.sumOf { it.weight })
    }

    @Test
    fun `overall progress is monotonic and ends at one hundred`() {
        var last = -1
        InstallStep.entries.forEach { step ->
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val p = step.overallPercent(fraction)
                assertTrue("$step $fraction -> $p after $last", p >= last)
                last = p
            }
        }
        assertEquals(100, InstallStep.entries.last().overallPercent(1f))
        assertEquals(0, InstallStep.CheckSpace.overallPercent(0f))
    }
}
