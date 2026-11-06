package tech.zakomi.zakdroid.runtime

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import tech.zakomi.zakdroid.core.fs.FileEntry
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.core.fs.FileSystem
import tech.zakomi.zakdroid.core.workspace.StoragePaths
import tech.zakomi.zakdroid.core.workspace.Workspace
import tech.zakomi.zakdroid.core.workspace.WorkspaceKind
import java.io.File
import java.nio.file.Files

/** In-memory SAF folder whose provider reports [lastModified] for every file (0 = unknown). */
private class FakeSafFs(var lastModified: Long) : FileSystem {
    val files = linkedMapOf<String, ByteArray>()
    private fun ref(name: String) = FileRef.Saf("content://test/$name")

    override suspend fun list(dir: FileRef) =
        files.map { (name, bytes) -> FileEntry(ref(name), name, false, bytes.size.toLong(), lastModified) }
    override suspend fun stat(ref: FileRef) = null
    override suspend fun read(ref: FileRef, maxBytes: Long) = files.getValue(ref.key.substringAfterLast('/'))
    override suspend fun write(ref: FileRef, bytes: ByteArray) = error("unused")
    override suspend fun createFile(parent: FileRef, name: String) = error("unused")
    override suspend fun createDirectory(parent: FileRef, name: String) = error("unused")
    override suspend fun rename(ref: FileRef, newName: String) = error("unused")
    override suspend fun delete(ref: FileRef) = error("unused")
    override suspend fun move(ref: FileRef, currentParent: FileRef, newParent: FileRef) = error("unused")
}

class WorkspaceStagerTest {
    private val paths = StoragePaths(Files.createTempDirectory("zd-stage").toFile())
    private val workspace = Workspace("demo", FileRef.Saf("content://test/root"), WorkspaceKind.Saf)

    private fun staged(guestPath: String) = File(paths.homeDir, guestPath.removePrefix(StoragePaths.GUEST_HOME))

    private suspend fun stage(stager: WorkspaceStager, name: String) =
        stager.prepare(workspace, FileRef.Saf("content://test/$name"), name)

    @Test
    fun `same-size edit is staged when the provider reports no modification time`() = runTest {
        val fs = FakeSafFs(lastModified = 0)
        val stager = WorkspaceStager(paths, fs, StandardTestDispatcher(testScheduler))
        fs.files["main.c"] = "int a=1;".toByteArray()
        fs.files["util.h"] = "#define X 1".toByteArray()
        stage(stager, "main.c")
        // Same sizes, no timestamps: only the content tells the edits apart.
        fs.files["main.c"] = "int a=2;".toByteArray()
        fs.files["util.h"] = "#define X 2".toByteArray()
        val guest = stage(stager, "main.c")
        assertEquals("int a=2;", staged(guest).readText())
        assertEquals("#define X 2", File(staged(guest).parentFile, "util.h").readText())
    }

    @Test
    fun `the file being run is restaged even when its timestamp did not change`() = runTest {
        val fs = FakeSafFs(lastModified = 1_700_000_000_000)
        val stager = WorkspaceStager(paths, fs, StandardTestDispatcher(testScheduler))
        fs.files["main.c"] = "int a=1;".toByteArray()
        stage(stager, "main.c")
        fs.files["main.c"] = "int a=2;".toByteArray()
        assertEquals("int a=2;", staged(stage(stager, "main.c")).readText())
    }

    @Test
    fun `files deleted from the folder are removed from the copy`() = runTest {
        val fs = FakeSafFs(lastModified = 0)
        val stager = WorkspaceStager(paths, fs, StandardTestDispatcher(testScheduler))
        fs.files["main.c"] = "int main(){}".toByteArray()
        fs.files["old.c"] = "x".toByteArray()
        val guest = stage(stager, "main.c")
        fs.files.remove("old.c")
        stage(stager, "main.c")
        assertFalse(File(staged(guest).parentFile, "old.c").exists())
    }
}
