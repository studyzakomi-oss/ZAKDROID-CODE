package tech.zakomi.zakdroid.core.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {
    @Test
    fun `rejects names that are not a single path segment`() {
        assertNotNull(FileNames.validate(""))
        assertNotNull(FileNames.validate("  "))
        assertNotNull(FileNames.validate("."))
        assertNotNull(FileNames.validate(".."))
        assertNotNull(FileNames.validate("a/b"))
        assertNotNull(FileNames.validate("x".repeat(256)))
    }

    @Test
    fun `accepts ordinary names`() {
        assertNull(FileNames.validate("main.c"))
        assertNull(FileNames.validate(".gitignore"))
        assertNull(FileNames.validate("ملف.py"))
    }

    @Test
    fun `file refs round-trip through their key`() {
        val local = FileRef.Local("/data/user/0/tech.zakomi.zakdroid/files/home/projects/a/main.c")
        val saf = FileRef.Saf("content://com.android.externalstorage.documents/tree/primary%3ACode/document/primary%3ACode%2Fmain.c")
        assertEquals(local, FileRef.fromKey(local.key))
        assertEquals(saf, FileRef.fromKey(saf.key))
    }
}
