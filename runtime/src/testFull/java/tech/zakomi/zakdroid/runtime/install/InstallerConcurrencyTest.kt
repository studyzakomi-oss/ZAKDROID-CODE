package tech.zakomi.zakdroid.runtime.install

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tech.zakomi.zakdroid.runtime.InstallStep
import tech.zakomi.zakdroid.runtime.RuntimeErrorKind
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Fake platform that reproduces the device bug: processes of an exec survive cancellation
 * (like PRoot's guests after SIGKILL) and keep the apk database lock until killed.
 */
private class FakePlatform : InstallPlatform {
    val events = mutableListOf<String>()
    val alive = mutableSetOf<String>()
    var downloads = 0
    var extracts = 0
    var runningExecs = 0
    var maxConcurrentExecs = 0
    var lockFailuresToInject = 0
    var networkFailuresToInject = 0
    var resolvedAddresses = listOf("151.101.2.132")
    var hostsDuringExec: String? = null
    lateinit var hostsFile: File
    var blockExec: CompletableDeferred<Unit>? = null
    var slowCleanup: CompletableDeferred<Unit>? = null
    private var nextPid = 100

    override fun deviceArch() = AlpineRelease.Arch.Aarch64
    override fun checkPrerequisites() = Unit
    override fun freeBytes(dir: File) = Long.MAX_VALUE

    override suspend fun download(url: String, target: File, expectedSize: Long, onProgress: (Float) -> Unit) {
        downloads++
        target.parentFile?.mkdirs()
        target.writeText("rootfs")
    }

    override fun sha256(file: File) = AlpineRelease.Arch.Aarch64.sha256

    override suspend fun extract(archive: File, target: File, onProgress: (Float) -> Unit) {
        extracts++
        File(target, "etc/apk").mkdirs()
    }

    override suspend fun exec(script: String, onLine: (String) -> Unit): ExecResult {
        if (alive.isNotEmpty() || lockFailuresToInject > 0) {
            if (lockFailuresToInject > 0) lockFailuresToInject--
            events += "exec-locked"
            return ExecResult(99, listOf("ERROR: Unable to lock database: Resource temporarily unavailable"))
        }
        if (::hostsFile.isInitialized && script.contains(" add ")) hostsDuringExec = hostsFile.readText()
        if (networkFailuresToInject > 0 && script.contains(" add ")) {
            networkFailuresToInject--
            events += "exec-network-error"
            return ExecResult(9, listOf("ERROR: readline-8.3.3-r1: DNS: transient error (try again later)", "(44/45) Installing python3-pyc"))
        }
        val pid = "apk-${nextPid++}"
        alive += pid
        runningExecs++
        maxConcurrentExecs = maxOf(maxConcurrentExecs, runningExecs)
        events += "exec-start"
        try {
            blockExec?.await()
        } finally {
            runningExecs--
            slowCleanup?.let { withContext(NonCancellable) { it.await() } }
        }
        alive -= pid // normal exit
        events += "exec-end"
        return ExecResult(0, listOf("OK: 63 packages"))
    }

    override fun killJobProcesses(): Int {
        val n = alive.size
        alive.clear()
        events += "kill($n)"
        return n
    }

    override fun jobPids() = emptyList<Int>()

    override suspend fun resolve(host: String) = resolvedAddresses

    override fun dnsServers() = listOf("192.168.1.1")
}

class InstallerConcurrencyTest {
    private val dir: File = Files.createTempDirectory("zd-install").toFile()
    private val paths = RuntimePaths(File(dir, "lib"), File(dir, "files"), File(dir, "home"))
    private val log = InstallLog(paths.installLog, paths.previousInstallLog)
    private val guard = InstallGuard(paths.installLock)
    private val platform = FakePlatform().also { it.hostsFile = File(paths.rootfs, "etc/hosts") }

    private fun TestScope.installer() =
        // EmptyCoroutineContext: stay on the test dispatcher (virtual time) instead of Dispatchers.IO.
        RuntimeInstaller(paths, platform, log, EmptyCoroutineContext) { _, _, _ -> }

    private suspend fun TestScope.guardedRun(mode: InstallMode = InstallMode.Install) =
        guard.withInstallLock(onLockBusy = { platform.killJobProcesses() }, describe = { "test" }) {
            installer().run(mode)
        }

    @Test
    fun `double Retry tap never runs two installers at once`() = runTest {
        platform.blockExec = CompletableDeferred()
        val first = async { guardedRun() }
        val second = async { guardedRun() }
        runCurrent()
        assertTrue("guard must report an active run", guard.active.value)
        assertEquals(1, platform.runningExecs)
        platform.blockExec!!.complete(Unit)
        listOf(first, second).awaitAll()
        assertEquals(1, platform.maxConcurrentExecs)
        assertEquals("the second run resumes instead of downloading again", 1, platform.downloads)
        assertEquals(1, platform.extracts)
        assertTrue(paths.readyMarker.isFile)
        assertFalse(guard.active.value)
    }

    @Test
    fun `cancel then retry waits for cleanup and kills the orphaned apk first`() = runTest {
        platform.blockExec = CompletableDeferred()
        platform.slowCleanup = CompletableDeferred()
        val first = launch { guardedRun() }
        runCurrent()
        assertEquals(1, platform.alive.size)

        val cancel = launch { first.cancelAndJoin() }
        val retry = async { guardedRun() }
        runCurrent()
        assertTrue("the cancelled run is still cleaning up", guard.active.value)
        assertEquals("retry must not start while the old run holds the guard", 1, platform.events.count { it == "exec-start" })

        platform.blockExec = null
        platform.slowCleanup!!.complete(Unit)
        platform.slowCleanup = null
        cancel.join()
        retry.await()

        // The cancelled exec left its apk running (orphan). The retry killed it before its own exec.
        val killIndex = platform.events.indexOfFirst { it == "kill(1)" }
        val secondExec = platform.events.withIndex().filter { it.value == "exec-start" }[1].index
        assertTrue("orphan killed before the retry's exec: ${platform.events}", killIndex in 0 until secondExec)
        assertFalse(platform.events.contains("exec-locked"))
        assertTrue(paths.readyMarker.isFile)
    }

    @Test
    fun `resume after app kill skips finished steps, ignores the stale lock file and kills orphans`() = runTest {
        // State left by an app process that was killed during InstallPackages:
        InstallJournal(paths.journal).apply {
            listOf(InstallStep.CheckSpace, InstallStep.Download, InstallStep.Verify, InstallStep.Extract, InstallStep.Configure).forEach(::markDone)
        }
        File(paths.rootfs, "etc/apk").mkdirs()
        paths.installLock.parentFile!!.mkdirs()
        paths.installLock.writeText("app pid=4242 work=old\n") // no OS lock: its holder is dead
        platform.alive += "apk-orphan-from-killed-app"

        guardedRun()

        assertEquals(0, platform.downloads)
        assertEquals(0, platform.extracts)
        assertEquals("kill(1)", platform.events.first())
        assertFalse(platform.events.contains("exec-locked"))
        assertTrue(paths.readyMarker.isFile)
        assertTrue(paths.installLog.readText().contains("killed 1 leftover install process(es)"))
    }

    @Test
    fun `a lock held for the whole wait is retried once after cleanup`() = runTest {
        platform.lockFailuresToInject = 1
        guardedRun()
        assertEquals(1, platform.events.count { it == "exec-locked" })
        assertTrue(paths.readyMarker.isFile)
    }

    @Test
    fun `a lock that stays busy becomes a clear Locked error`() = runTest {
        platform.lockFailuresToInject = 2
        try {
            guardedRun()
            fail("expected InstallException")
        } catch (e: InstallException) {
            assertEquals(RuntimeErrorKind.Locked, e.kind)
        }
        assertFalse(paths.readyMarker.isFile)
        assertFalse(guard.active.value)
        assertTrue(paths.installLog.readText().contains("apk database locked"))
    }

    @Test
    fun `DNS errors from apk are retried and the mirror is pinned only during the install`() = runTest {
        platform.networkFailuresToInject = 2
        guardedRun()
        assertEquals(2, platform.events.count { it == "exec-network-error" })
        assertTrue(paths.readyMarker.isFile)
        // Pinned while apk ran, removed afterwards.
        assertTrue(platform.hostsDuringExec!!.contains("151.101.2.132 ${AlpineRelease.MIRROR_HOST}"))
        assertFalse(File(paths.rootfs, "etc/hosts").readText().contains(AlpineRelease.MIRROR_HOST))
        val resolv = File(paths.rootfs, "etc/resolv.conf").readText()
        assertTrue("network DNS first: $resolv", resolv.startsWith("nameserver 192.168.1.1\n"))
    }

    @Test
    fun `persistent network errors become a Network error after three attempts`() = runTest {
        platform.networkFailuresToInject = 3
        try {
            guardedRun()
            fail("expected InstallException")
        } catch (e: InstallException) {
            assertEquals(RuntimeErrorKind.Network, e.kind)
        }
        assertEquals(3, platform.events.count { it == "exec-network-error" })
        assertFalse(File(paths.rootfs, "etc/hosts").readText().contains(AlpineRelease.MIRROR_HOST))
    }

    @Test
    fun `no internet is reported before apk runs`() = runTest {
        platform.resolvedAddresses = emptyList()
        try {
            guardedRun()
            fail("expected InstallException")
        } catch (e: InstallException) {
            assertEquals(RuntimeErrorKind.Network, e.kind)
        }
        assertFalse(platform.events.any { it.startsWith("exec") })
    }

    @Test
    fun `apk transient DNS errors are recognised as network failures`() {
        assertTrue(RuntimeInstaller.isNetworkFailure(ExecResult(9, listOf("ERROR: sqlite-libs-3.53.4-r0: DNS: transient error (try again later)"))))
        assertFalse(RuntimeInstaller.isNetworkFailure(ExecResult(1, listOf("ERROR: unable to select packages:", "  foo (no such package)"))))
        assertFalse(RuntimeInstaller.isNetworkFailure(ExecResult(0, listOf("OK"))))
    }

    @Test
    fun `apk is asked to wait 60 seconds for its lock`() {
        assertEquals(60, RuntimeInstaller.APK_LOCK_WAIT_SECONDS)
        assertTrue(RuntimeInstaller.isLockFailure(ExecResult(99, listOf("ERROR: Unable to lock database: Resource temporarily unavailable"))))
        assertFalse(RuntimeInstaller.isLockFailure(ExecResult(1, listOf("ERROR: unable to select packages"))))
    }
}
