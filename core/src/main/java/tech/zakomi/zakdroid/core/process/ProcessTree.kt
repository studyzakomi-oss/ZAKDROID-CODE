package tech.zakomi.zakdroid.core.process

import java.io.File

/**
 * Finds and kills the app's own child processes through /proc.
 *
 * Needed because killing PRoot does not stop the programs it runs: PRoot ignores
 * SIGTERM, and after SIGKILL its guest processes are re-parented to init and keep
 * running (e.g. an `apk` that keeps holding the package database lock).
 * Android only exposes the app's own processes in /proc, so nothing else is touched.
 */
class ProcessTree(
    private val procRoot: File = File("/proc"),
    private val selfPid: Int,
    private val kill: (pid: Int) -> Unit,
) {
    /** All descendants of [pid], deepest first. */
    fun descendants(pid: Int): List<Int> {
        val children = HashMap<Int, MutableList<Int>>()
        pids().forEach { p -> parentOf(p)?.let { children.getOrPut(it) { mutableListOf() } += p } }
        val result = ArrayList<Int>()
        fun visit(p: Int) {
            children[p].orEmpty().forEach { child ->
                visit(child)
                result += child
            }
        }
        visit(pid)
        return result
    }

    /** SIGKILLs [pid] and everything below it, children first. */
    fun killTree(pid: Int) {
        if (pid <= 0 || pid == selfPid) return
        descendants(pid).forEach(::safeKill)
        safeKill(pid)
    }

    /** SIGKILLs every process whose environment contains exactly [entry] (e.g. "ZAKDROID_JOB=install"). */
    fun killTagged(entry: String): Int {
        val tagged = pids().filter { it != selfPid && environOf(it)?.contains(entry) == true }
        tagged.forEach(::safeKill)
        return tagged.size
    }

    private fun safeKill(pid: Int) {
        if (pid != selfPid) runCatching { kill(pid) }
    }

    private fun pids(): List<Int> =
        procRoot.list().orEmpty().mapNotNull { it.toIntOrNull() }

    /** Parent pid from /proc/<pid>/stat; the command name may contain spaces and ')'. */
    internal fun parentOf(pid: Int): Int? = runCatching {
        val stat = File(procRoot, "$pid/stat").readText()
        stat.substring(stat.lastIndexOf(')') + 2).split(' ')[1].toInt()
    }.getOrNull()

    private fun environOf(pid: Int): List<String>? = runCatching {
        File(procRoot, "$pid/environ").readText().split('\u0000')
    }.getOrNull()

    companion object {
        /** Set in the host and guest environment of every PRoot invocation. */
        const val GUEST_TAG = "ZAKDROID_GUEST=1"

        /** The instance for this app process. */
        val current: ProcessTree by lazy {
            ProcessTree(
                selfPid = android.os.Process.myPid(),
                kill = { pid -> android.os.Process.sendSignal(pid, android.os.Process.SIGNAL_KILL) },
            )
        }
    }
}
