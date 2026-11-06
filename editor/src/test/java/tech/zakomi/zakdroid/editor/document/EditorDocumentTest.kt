package tech.zakomi.zakdroid.editor.document

import io.github.rosemoe.sora.text.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.editor.EditorLanguage

class EditorDocumentTest {

    private fun document(text: String, lineEnding: LineEnding = LineEnding.LF) =
        EditorDocument(FileRef.Local("/x/main.c"), "main.c", EditorLanguage.C, Content(text), lineEnding, hasBom = false)

    @Test
    fun editThenUndoIsClean() {
        val doc = document("int main;\n")
        doc.content.insert(0, 3, "x")
        assertTrue(doc.dirty.value)
        doc.content.undoManager.undo(doc.content)
        assertEquals("int main;\n", doc.content.toString())
        assertFalse(doc.dirty.value)
    }

    @Test
    fun typingBackTheSavedTextIsClean() {
        val doc = document("abc")
        doc.content.delete(0, 2, 0, 3)
        assertTrue(doc.dirty.value)
        doc.content.insert(0, 2, "c")
        assertFalse(doc.dirty.value)
    }

    @Test
    fun sameLengthDifferentTextIsDirty() {
        val doc = document("abc")
        doc.content.replace(0, 1, 0, 2, "X")
        assertEquals("aXc", doc.content.toString())
        assertTrue(doc.dirty.value)
    }

    @Test
    fun saveMovesTheCleanPoint() {
        val doc = document("a\nb", LineEnding.CRLF)
        doc.content.insert(1, 1, "c")
        val snapshot = doc.snapshotForSave()
        assertEquals("a\r\nbc", snapshot.diskText)
        doc.markSaved(snapshot)
        assertFalse(doc.dirty.value)
        doc.content.undoManager.undo(doc.content)
        assertEquals("a\nb", doc.content.toString())
        assertTrue(doc.dirty.value)
    }
}
