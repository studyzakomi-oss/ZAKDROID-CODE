package tech.zakomi.zakdroid.core.process

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class ProcessTreeTest {

    private fun fakeProc(vararg procs: Triple<Int, Int, String>): File {
        val root = Files.createTempDirectory("proc").toFile()
        procs.forEach { (pid, ppid, env) ->
            File(root, "$pid").mkdirs()
            File(root, "$pid/stat").writeText("$pid (weird) name) S $ppid 1 1 0 -1")
            File(root, "$pid/environ").writeText(env.replace(';', '\u0000'))
        }
        return root
    }

    @Test
    fun `descendants are found through the whole tree, deepest first`() {
        val proc = fakeProc(
            Triple(10, 1, ""), Triple(11, 10, ""), Triple(12, 11, ""), Triple(13, 10, ""), Triple(20, 1, ""),
        )
        val tree = ProcessTree(proc, selfPid = 1, kill = {})
        val d = tree.descendants(10)
        assertEquals(setOf(11, 12, 13), d.toSet())
        assertTrue(d.indexOf(12) < d.indexOf(11))
    }

    @Test
    fun `killTree kills children before the root and never itself`() {
        val proc = fakeProc(Triple(10, 1, ""), Triple(11, 10, ""), Triple(12, 11, ""))
        val killed = mutableListOf<Int>()
        ProcessTree(proc, selfPid = 1, kill = { killed += it }).killTree(10)
        assertEquals(listOf(12, 11, 10), killed)
        ProcessTree(proc, selfPid = 10, kill = { killed += it }).killTree(10)
        assertEquals(3, killed.size)
    }

    @Test
    fun `killTagged matches whole environment entries only`() {
        val proc = fakeProc(
            Triple(10, 1, "A=1;ZAKDROID_JOB=install;B=2"),
            Triple(11, 1, "ZAKDROID_JOB=installer"),
            Triple(12, 1, "ZAKDROID_JOB=install"),
            Triple(5, 1, "ZAKDROID_JOB=install"),
        )
        val killed = mutableListOf<Int>()
        val n = ProcessTree(proc, selfPid = 5, kill = { killed += it }).killTagged("ZAKDROID_JOB=install")
        assertEquals(2, n)
        assertEquals(setOf(10, 12), killed.toSet())
    }

    @Test
    fun `real processes - an orphan-prone child tree is killed completely`() {
        assumeTrue(File("/proc/self/stat").exists())
        // Unit tests compile against android.jar, which has no Process.pid(): let the shell print it.
        val shell = ProcessBuilder("sh", "-c", "echo \$\$; sleep 300 & sleep 300 & wait").start()
        val pid = shell.inputStream.bufferedReader().readLine().trim().toInt()
        Thread.sleep(500)
        val self = File("/proc/self").canonicalFile.name.toInt()
        val tree = ProcessTree(selfPid = self, kill = { p ->
            ProcessBuilder("kill", "-9", "$p").start().waitFor()
        })
        val children = tree.descendants(pid)
        assertEquals(2, children.size)
        tree.killTree(pid)
        assertTrue(shell.waitFor(5, TimeUnit.SECONDS))
        Thread.sleep(200)
        children.forEach { assertFalse("child $it still alive", File("/proc/$it").exists()) }
    }
}
