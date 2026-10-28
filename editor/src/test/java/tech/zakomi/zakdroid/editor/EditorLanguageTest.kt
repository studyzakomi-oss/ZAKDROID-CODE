package tech.zakomi.zakdroid.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLanguageTest {
    @Test
    fun `maps file extensions like VS Code`() {
        assertEquals(EditorLanguage.C, EditorLanguage.fromFileName("main.c"))
        assertEquals(EditorLanguage.Cpp, EditorLanguage.fromFileName("main.cpp"))
        assertEquals(EditorLanguage.Cpp, EditorLanguage.fromFileName("lib.hpp"))
        assertEquals(EditorLanguage.Cpp, EditorLanguage.fromFileName("header.h"))
        assertEquals(EditorLanguage.Python, EditorLanguage.fromFileName("app.py"))
        assertEquals(EditorLanguage.Python, EditorLanguage.fromFileName("APP.PY"))
        assertEquals(EditorLanguage.PlainText, EditorLanguage.fromFileName("README"))
        assertEquals(EditorLanguage.PlainText, EditorLanguage.fromFileName("notes.txt"))
    }
}
