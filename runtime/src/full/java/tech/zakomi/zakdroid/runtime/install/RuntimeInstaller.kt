package tech.zakomi.zakdroid.runtime.install

import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.process.ProcessTree
import tech.zakomi.zakdroid.runtime.InstallStep
import tech.zakomi.zakdroid.runtime.RuntimeErrorKind
import java.io.File
import java.util.concurrent.TimeUnit

/** Mode of an install run, chosen by the user in the UI. */
enum class InstallMode { Install, Repair, Reinstall }

/**
 * Performs the install steps in order. Each finished step is recorded in the
 * [InstallJournal], so a cancelled or failed run resumes from the step that did
 * not finish. Long-running parts check for coroutine cancellation.
 */
class RuntimeInstaller(
    private val paths: RuntimePaths,
    private val downloader: Downloader = Downloader(),
    private val onProgress: (step: InstallStep, fraction: Float, detail: String?) -> Unit,
) {
    private val journal = InstallJournal(paths.journal)
    private val proot = ProotCommand(paths)

    suspend fun run(mode: InstallMode) = withContext(Dispatchers.IO) {
        val arch = AlpineRelease.deviceArch()
            ?: throw InstallException(RuntimeErrorKind.UnsupportedDevice, "This device's CPU is not supported (arm64-v8a or x86_64 required).")
        if (!paths.proot.exists() || !paths.prootLoader.exists()) {
            throw InstallException(RuntimeErrorKind.UnsupportedDevice, "PRoot is missing from this installation. Reinstall zakdroid-code from GitHub Releases or F-Droid.")
        }
        paths.readyMarker.delete()
        when (mode) {
            InstallMode.Install -> Unit
            InstallMode.Repair -> journal.invalidateFrom(InstallStep.Configure)
            InstallMode.Reinstall -> {
                journal.clear()
                paths.rootfs.deleteRecursively()
            }
        }
        val archive = File(paths.downloads, arch.fileName)

        step(InstallStep.CheckSpace) {
            val needed = if (journal.isDone(InstallStep.Extract)) AlpineRelease.REQUIRED_FREE_BYTES / 2 else AlpineRelease.REQUIRED_FREE_BYTES
            paths.runtimeDir.mkdirs()
            val available = StatFs(paths.runtimeDir.path).availableBytes
            if (available < needed) {
                throw InstallException(
                    RuntimeErrorKind.NotEnoughSpace,
                    "Not enough storage: ${mb(needed)} MB of free space is needed, ${mb(available)} MB is available. Free up space and tap Install again.",
                )
            }
        }
        step(InstallStep.Download, skipIfDone = true) {
            downloader.download(arch.url, archive, arch.size) { onProgress(InstallStep.Download, it, "${arch.fileName}") }
        }
        step(InstallStep.Verify, skipIfDone = true) {
            val actual = Downloader.sha256(archive)
            if (actual != arch.sha256) {
                archive.delete()
                journal.invalidateFrom(InstallStep.Download)
                throw InstallException(RuntimeErrorKind.Checksum, "Checksum mismatch for ${arch.fileName}. The file was deleted; tap Install to download it again.")
            }
        }
        step(InstallStep.Extract, skipIfDone = true) {
            // A partial extraction cannot be trusted; start from an empty directory.
            paths.rootfs.deleteRecursively()
            RootfsExtractor.extract(archive, paths.rootfs) { onProgress(InstallStep.Extract, it, null) }
        }
        step(InstallStep.Configure) { configure() }
        step(InstallStep.InstallPackages) { installPackages(mode == InstallMode.Repair) }
        step(InstallStep.VerifyToolchain) { verifyToolchain() }

        archive.delete()
        paths.readyMarker.writeText(AlpineRelease.VERSION)
    }

    private suspend fun step(step: InstallStep, skipIfDone: Boolean = false, block: suspend () -> Unit) {
        currentCoroutineContext().ensureActive()
        if (skipIfDone && journal.isDone(step)) return
        onProgress(step, 0f, null)
        block()
        journal.markDone(step)
        onProgress(step, 1f, null)
    }

    private fun configure() {
        val root = paths.rootfs
        File(root, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(root, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
        File(root, "etc/apk/repositories").writeText(AlpineRelease.REPOSITORIES.joinToString("\n", postfix = "\n"))
        File(root, "etc/profile.d").mkdirs()
        File(root, "etc/profile.d/zakdroid.sh").writeText(
            """
            |# zakdroid-code shell defaults
            |export PS1='\[\033[1;34m\]\w\[\033[0m\] \$ '
            |export PYTHONDONTWRITEBYTECODE=1
            |alias ll='ls -la'
            |""".trimMargin(),
        )
        File(root, "tmp").apply { mkdirs(); setWritable(true, false) }
        paths.prootTmp.mkdirs()
        paths.home.mkdirs()
    }

    private suspend fun installPackages(repair: Boolean) {
        // --wait: if a user's own `apk` (terminal) holds the lock, wait instead of failing at once.
        val apk = "apk --wait 30 --no-progress"
        val fix = if (repair) "$apk fix && " else ""
        val script = "set -e; $apk update && ${fix}$apk add ${AlpineRelease.PACKAGES.joinToString(" ")}"
        val progress = Regex("""^\((\d+)/(\d+)\)\s+(\S+)\s+(\S+)""")
        val output = exec(script) { line ->
            progress.find(line)?.let { m ->
                val (n, total, action, pkg) = m.destructured
                onProgress(InstallStep.InstallPackages, n.toFloat() / total.toFloat(), "$action $pkg")
            }
        }
        if (output.exitCode != 0) {
            val network = output.tail.any { "temporary error" in it || "network" in it.lowercase() || "DNS" in it }
            throw InstallException(
                if (network) RuntimeErrorKind.Network else RuntimeErrorKind.Packages,
                "Package installation failed (exit ${output.exitCode}):\n" + output.tail.takeLast(12).joinToString("\n"),
            )
        }
    }

    private suspend fun verifyToolchain() {
        val output = exec("clang --version | head -n 1 && python3 --version")
        if (output.exitCode != 0) {
            throw InstallException(RuntimeErrorKind.Verification, "Toolchain check failed:\n" + output.tail.takeLast(8).joinToString("\n"))
        }
    }

    private class ExecResult(val exitCode: Int, val tail: List<String>)

    /**
     * Runs [script] with /bin/sh inside the rootfs, streaming output lines to [onLine].
     * Every process of the job carries [JOB_TAG]; leftovers of an earlier, interrupted
     * run (which would still hold apk's database lock) are killed first, and all of the
     * job's processes are killed when it ends or is cancelled.
     */
    private suspend fun exec(script: String, onLine: (String) -> Unit = {}): ExecResult {
        killJobProcesses()
        val argv = proot.argv("/", listOf("/bin/sh", "-c", script), extraEnv = listOf(JOB_TAG))
        val process = ProcessBuilder(argv).apply {
            environment().clear()
            proot.hostEnv(listOf(JOB_TAG)).forEach { kv -> kv.split("=", limit = 2).let { environment()[it[0]] = it[1] } }
            redirectErrorStream(true)
            directory(paths.runtimeDir)
        }.start()
        val tail = ArrayDeque<String>()
        return coroutineScope {
            // Reading output blocks; on cancellation, killing PRoot ends the read (and, with
            // --kill-on-exit, every guest process).
            val watchdog = launch {
                try {
                    awaitCancellation()
                } finally {
                    // SIGTERM is ignored by PRoot; kill the whole job so the blocked read returns.
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

    private fun killJobProcesses() {
        val killed = ProcessTree.current.killTagged(JOB_TAG)
        if (killed > 0) Log.i(TAG, "killed $killed leftover install process(es)")
    }

    private fun mb(bytes: Long) = bytes / (1024 * 1024)

    private companion object {
        const val JOB_TAG = "ZAKDROID_JOB=install"
        const val TAG = "RuntimeInstaller"
    }
}
