package tech.zakomi.zakdroid.runtime.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.zakomi.zakdroid.runtime.InstallStep
import java.io.File
import java.nio.file.Files

class InstallJournalTest {
    private val file = File(Files.createTempDirectory("journal").toFile(), "journal.properties")

    @Test
    fun `completed steps survive a restart`() {
        InstallJournal(file).apply {
            markDone(InstallStep.Download)
            markDone(InstallStep.Verify)
        }
        val reloaded = InstallJournal(file)
        assertTrue(reloaded.isDone(InstallStep.Download))
        assertTrue(reloaded.isDone(InstallStep.Verify))
        assertFalse(reloaded.isDone(InstallStep.Extract))
        assertTrue(reloaded.hasProgress())
    }

    @Test
    fun `invalidating a step forgets it and every later step`() {
        val journal = InstallJournal(file)
        InstallStep.entries.forEach(journal::markDone)
        journal.invalidateFrom(InstallStep.Configure)
        assertTrue(journal.isDone(InstallStep.Extract))
        assertFalse(journal.isDone(InstallStep.Configure))
        assertFalse(journal.isDone(InstallStep.VerifyToolchain))
    }

    @Test
    fun `clear removes all progress`() {
        val journal = InstallJournal(file)
        journal.markDone(InstallStep.Download)
        journal.clear()
        assertFalse(InstallJournal(file).hasProgress())
    }
}
