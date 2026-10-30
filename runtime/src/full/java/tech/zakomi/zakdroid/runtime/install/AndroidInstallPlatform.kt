package tech.zakomi.zakdroid.runtime.install

import android.content.Context
import android.net.ConnectivityManager
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.process.ProcessTree
import tech.zakomi.zakdroid.runtime.RuntimeErrorKind
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidInstallPlatform @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paths: RuntimePaths,
) : InstallPlatform {
    private val downloader = Downloader()
    private val proot = ProotCommand(paths)
    private val processes get() = ProcessTree.current

    override fun deviceArch() = AlpineRelease.deviceArch()

    override fun checkPrerequisites() {
        if (!paths.proot.exists() || !paths.prootLoader.exists()) {
            throw InstallException(RuntimeErrorKind.UnsupportedDevice, "PRoot is missing from this installation. Reinstall zakdroid-code from GitHub Releases or F-Droid.")
        }
    }

    override fun freeBytes(dir: File): Long = StatFs(dir.path).availableBytes

    override suspend fun download(url: String, target: File, expectedSize: Long, onProgress: (Float) -> Unit) =
        downloader.download(url, target, expectedSize, onProgress)

    override fun sha256(file: File) = Downloader.sha256(file)

    override suspend fun extract(archive: File, target: File, onProgress: (Float) -> Unit) =
        RootfsExtractor.extract(archive, target, onProgress)

    override suspend fun exec(script: String, onLine: (String) -> Unit): ExecResult {
        killJobProcesses()
        // --kill-on-exit makes PRoot kill its guests when it exits normally. It cannot help
        // when PRoot itself is SIGKILLed (its guests are then re-parented to init), which is
        // why every process also carries JOB_TAG and is killed through /proc.
        val argv = proot.argv("/", listOf("/bin/sh", "-c", script), extraEnv = listOf(JOB_TAG))
        val process = ProcessBuilder(argv).apply {
            environment().clear()
            proot.hostEnv(listOf(JOB_TAG)).forEach { kv -> kv.split("=", limit = 2).let { environment()[it[0]] = it[1] } }
            redirectErrorStream(true)
            directory(paths.runtimeDir)
        }.start()
        val tail = ArrayDeque<String>()
        return coroutineScope {
            val watchdog = launch {
                try {
                    awaitCancellation()
                } finally {
                    // SIGTERM is ignored by PRoot; killing the job ends the blocked read below.
                    killJobProcesses()
                }
            }
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (tail.size == 40) tail.removeFirst()
                        tail.addLast(line)
                        onLine(line)
                    }
                }
                ensureActive()
                if (!process.waitFor(10, TimeUnit.MINUTES)) {
                    throw InstallException(RuntimeErrorKind.Packages, "Timed out waiting for the Linux runtime")
                }
                ExecResult(process.exitValue(), tail.toList())
            } finally {
                watchdog.cancel()
                killJobProcesses()
            }
        }
    }

    override fun killJobProcesses(): Int = processes.killTagged(JOB_TAG)

    override fun jobPids(): List<Int> = processes.findTagged(JOB_TAG)

    override suspend fun resolve(host: String): List<String> = withContext(Dispatchers.IO) {
        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (_: UnknownHostException) {
            emptyList()
        }
        // IPv4 only when available: the guest may have no IPv6 route even if Android has one.
        val v4 = addresses.filterIsInstance<Inet4Address>()
        (v4.ifEmpty { addresses }).mapNotNull { it.hostAddress }.distinct()
    }

    override fun dnsServers(): List<String> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val servers = cm.getLinkProperties(cm.activeNetwork)?.dnsServers.orEmpty()
        return servers.sortedBy { if (it is Inet4Address) 0 else 1 }.mapNotNull { it.hostAddress?.substringBefore('%') }
    }

    private companion object {
        const val JOB_TAG = "ZAKDROID_JOB=install"
    }
}
