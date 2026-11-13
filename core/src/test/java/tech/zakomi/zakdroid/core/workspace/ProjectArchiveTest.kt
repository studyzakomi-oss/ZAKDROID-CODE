package tech.zakomi.zakdroid.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectArchiveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun project(parent: File, name: String, vararg files: Pair<String, String>): File {
        val dir = File(parent, name).apply { mkdirs() }
        files.forEach { (path, text) -> File(dir, path).apply { parentFile?.mkdirs() }.writeText(text) }
        return dir
    }

    private fun zipOf(vararg entries: Pair<String, String?>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                text?.let { zip.write(it.toByteArray()) }
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun import(zip: ByteArray, projects: File, fallback: String = "imported") =
        ProjectArchive.import(ByteArrayInputStream(zip), projects, tmp.newFolder(), fallback)

    @Test
    fun `export then import restores every file, empty folders and git data`() {
        val source = tmp.newFolder("source")
        val a = project(source, "hello", "main.c" to "int main(){}", "src/util/a.h" to "#pragma once", ".git/HEAD" to "ref: refs/heads/main\n")
        File(a, "empty").mkdirs()
        val b = project(source, "مشروع", "run.py" to "print('مرحبا')")
        val zip = ByteArrayOutputStream()

        val count = ProjectArchive.export(listOf(a, b), zip)

        assertEquals(4, count)
        val projects = tmp.newFolder("projects")
        val imported = import(zip.toByteArray(), projects)
        assertEquals(listOf("hello", "مشروع"), imported.map { it.name })
        assertEquals("int main(){}", File(projects, "hello/main.c").readText())
        assertEquals("#pragma once", File(projects, "hello/src/util/a.h").readText())
        assertEquals("ref: refs/heads/main\n", File(projects, "hello/.git/HEAD").readText())
        assertTrue(File(projects, "hello/empty").isDirectory)
        assertEquals("print('مرحبا')", File(projects, "مشروع/run.py").readText())
    }

    @Test
    fun `an existing project is kept and the imported one gets a suffix`() {
        val projects = tmp.newFolder("projects")
        project(projects, "hello", "main.c" to "old")
        project(projects, "hello-2", "main.c" to "older")

        val imported = import(zipOf("hello/main.c" to "new"), projects)

        assertEquals(listOf("hello-3"), imported.map { it.name })
        assertEquals("old", File(projects, "hello/main.c").readText())
        assertEquals("new", File(projects, "hello-3/main.c").readText())
    }

    @Test
    fun `files at the top level make one project named after the archive`() {
        val projects = tmp.newFolder("projects")

        val imported = import(zipOf("main.c" to "x", "lib/a.c" to "y"), projects, fallback = "homework")

        assertEquals(listOf("homework"), imported.map { it.name })
        assertEquals("y", File(projects, "homework/lib/a.c").readText())
    }

    @Test
    fun `paths that leave the archive are rejected and nothing is left behind`() {
        val projects = tmp.newFolder("projects")
        val scratch = tmp.newFolder("scratch")

        assertThrows(IllegalArgumentException::class.java) {
            ProjectArchive.import(ByteArrayInputStream(zipOf("ok/a.c" to "x", "../evil.sh" to "rm")), projects, scratch, "p")
        }

        assertTrue(projects.listFiles().orEmpty().isEmpty())
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
        assertFalse(File(tmp.root, "evil.sh").exists())
    }

    @Test
    fun `a file that is not a zip archive is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            import("not a zip".toByteArray(), tmp.newFolder("projects"))
        }
    }

    @Test
    fun `symbolic links are not exported`() {
        val source = tmp.newFolder("source")
        val secret = tmp.newFile("secret.txt").apply { writeText("secret") }
        val a = project(source, "hello", "main.c" to "x")
        Files.createSymbolicLink(File(a, "link.txt").toPath(), secret.toPath())
        val zip = ByteArrayOutputStream()

        assertEquals(1, ProjectArchive.export(listOf(a), zip))

        val projects = tmp.newFolder("projects")
        import(zip.toByteArray(), projects)
        assertFalse(File(projects, "hello/link.txt").exists())
    }
}
