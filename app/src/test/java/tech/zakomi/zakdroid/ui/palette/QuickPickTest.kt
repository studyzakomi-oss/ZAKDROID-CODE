package tech.zakomi.zakdroid.ui.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickPickTest {

    @Test
    fun modeFollowsTheFirstCharacter() {
        assertEquals(QuickPickMode.Files, QuickPickMode.of("main"))
        assertEquals(QuickPickMode.Files, QuickPickMode.of(""))
        assertEquals(QuickPickMode.Commands, QuickPickMode.of(">save"))
        assertEquals(QuickPickMode.Line, QuickPickMode.of(":12"))
        assertEquals(QuickPickMode.Symbols, QuickPickMode.of("@main"))
    }

    @Test
    fun lineTargets() {
        assertEquals(12 to 1, parseLineTarget("12"))
        assertEquals(12 to 5, parseLineTarget(" 12:5 "))
        assertEquals(12 to 5, parseLineTarget("12,5"))
        assertEquals(12 to 1, parseLineTarget("12:"))
        assertNull(parseLineTarget(""))
        assertNull(parseLineTarget("0"))
        assertNull(parseLineTarget("-3"))
        assertNull(parseLineTarget("abc"))
        assertNull(parseLineTarget("1:2:3"))
        assertNull(parseLineTarget("4:x"))
    }
}
