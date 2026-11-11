package tech.zakomi.zakdroid.editor.commands

import io.github.rosemoe.sora.text.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineCommandsTest {

    private fun cursor(line: Int, column: Int) = TextSelection(line, column, line, column)

    @Test
    fun commentAndUncommentOneLine() {
        val c = Content("    int x = 1;\n")
        val after = LineCommands.toggleLineComment(c, cursor(0, 8), "//")
        assertEquals("    // int x = 1;\n", c.toString())
        assertEquals(cursor(0, 11), after)
        val back = LineCommands.toggleLineComment(c, after!!, "//")
        assertEquals("    int x = 1;\n", c.toString())
        assertEquals(cursor(0, 8), back)
    }

    @Test
    fun commentIsOneUndoStep() {
        val c = Content("a\nb\nc")
        LineCommands.toggleLineComment(c, TextSelection(0, 0, 2, 1), "#")
        assertEquals("# a\n# b\n# c", c.toString())
        c.undoManager.undo(c)
        assertEquals("a\nb\nc", c.toString())
    }

    @Test
    fun commentAlignsAtTheSmallestIndentAndSkipsBlankLines() {
        val c = Content("  if x:\n\n      y()\n")
        LineCommands.toggleLineComment(c, TextSelection(0, 0, 2, 3), "#")
        assertEquals("  # if x:\n\n  #     y()\n", c.toString())
    }

    @Test
    fun mixedLinesGetCommentedNotUncommented() {
        val c = Content("// a\nb")
        LineCommands.toggleLineComment(c, TextSelection(0, 0, 1, 1), "//")
        assertEquals("// // a\n// b", c.toString())
    }

    @Test
    fun uncommentWithoutSpaceAfterToken() {
        val c = Content("//a\n// b")
        LineCommands.toggleLineComment(c, TextSelection(0, 0, 1, 1), "//")
        assertEquals("a\nb", c.toString())
    }

    @Test
    fun selectionEndingAtColumnZeroExcludesThatLine() {
        val c = Content("a\nb\nc")
        LineCommands.toggleLineComment(c, TextSelection(0, 0, 1, 0), "#")
        assertEquals("# a\nb\nc", c.toString())
    }

    @Test
    fun blankLineGetsAComment() {
        val c = Content("")
        assertEquals(cursor(0, 2), LineCommands.toggleLineComment(c, cursor(0, 0), "#"))
        assertEquals("# ", c.toString())
    }

    @Test
    fun duplicateDownMovesTheCursorToTheCopy() {
        val c = Content("a\nb")
        assertEquals(cursor(1, 1), LineCommands.duplicateLines(c, cursor(0, 1), selectCopy = true))
        assertEquals("a\na\nb", c.toString())
    }

    @Test
    fun duplicateLastLinesKeepsSelection() {
        val c = Content("a\nb\nc")
        val sel = TextSelection(1, 0, 2, 1)
        assertEquals(sel, LineCommands.duplicateLines(c, sel, selectCopy = false))
        assertEquals("a\nb\nc\nb\nc", c.toString())
    }

    @Test
    fun moveUpAndDown() {
        val c = Content("a\nb\nc\nd")
        val sel = LineCommands.moveLines(c, TextSelection(1, 0, 2, 1), up = true)
        assertEquals("b\nc\na\nd", c.toString())
        assertEquals(TextSelection(0, 0, 1, 1), sel)
        val down = LineCommands.moveLines(c, sel!!, up = false)
        assertEquals("a\nb\nc\nd", c.toString())
        assertEquals(TextSelection(1, 0, 2, 1), down)
    }

    @Test
    fun moveToTheLastLineAndBack() {
        val c = Content("a\nb")
        assertEquals(cursor(1, 1), LineCommands.moveLines(c, cursor(0, 1), up = false))
        assertEquals("b\na", c.toString())
        assertEquals(cursor(0, 1), LineCommands.moveLines(c, cursor(1, 1), up = true))
        assertEquals("a\nb", c.toString())
    }

    @Test
    fun moveStopsAtTheEdges() {
        val c = Content("a\nb")
        assertNull(LineCommands.moveLines(c, cursor(0, 0), up = true))
        assertNull(LineCommands.moveLines(c, cursor(1, 0), up = false))
        assertEquals("a\nb", c.toString())
    }

    @Test
    fun deleteMiddleLastAndOnlyLine() {
        val c = Content("one\ntwo\nx")
        assertEquals(cursor(1, 1), LineCommands.deleteLines(c, cursor(1, 2)))
        assertEquals("one\nx", c.toString())
        assertEquals(cursor(0, 1), LineCommands.deleteLines(c, cursor(1, 1)))
        assertEquals("one", c.toString())
        assertEquals(cursor(0, 0), LineCommands.deleteLines(c, cursor(0, 2)))
        assertEquals("", c.toString())
    }

    @Test
    fun trimTrailingWhitespaceAndFinalNewline() {
        val c = Content("a  \n\tb\t \n\n  ")
        assertEquals(3, DocumentCleanup.trimTrailingWhitespace(c))
        assertEquals("a\n\tb\n\n", c.toString())
        assertEquals(false, DocumentCleanup.ensureFinalNewline(c))
        val d = Content("x")
        assertEquals(true, DocumentCleanup.ensureFinalNewline(d))
        assertEquals("x\n", d.toString())
        assertEquals(false, DocumentCleanup.ensureFinalNewline(Content("")))
    }
}
