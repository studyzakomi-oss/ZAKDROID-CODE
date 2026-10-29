package tech.zakomi.zakdroid.runtime.install

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.runtime.InstallStep
import tech.zakomi.zakdroid.runtime.RuntimeErrorKind
import java.io.File
import kotlin.coroutines.CoroutineContext

/** Mode of an install run, chosen by the user in the UI. */
enum class InstallMode { Install, Repair, Reinstall }

/**
 * Performs the install steps in order. Each finished step is recorded in the
 * [InstallJournal], so a cancelled, failed or killed run resumes from the step that did
 * not finish. Callers must hold the [InstallGuard] (see [RuntimeInstallWorker]).
 */
class RuntimeInstaller(
    private val paths: RuntimePaths,
    private val platform: InstallPlatform,
    private val log: InstallLog,
    private val ioContext: CoroutineContext = Dispatchers.IO,
    private val onProgress: (step: InstallStep, fraction: Float, detail: String?) -> Unit,
) {
    private val journal = InstallJournal(paths.journal)

    suspend fun run(mode: InstallMode) = withContext(ioContext) {
        val arch = platform.deviceArch()
            ?: throw InstallException(RuntimeErrorKind.UnsupportedDevice, "This device's CPU is not supported (arm64-v8a or x86_64 required).")
        platform.checkPrerequisites()
        // Orphans of an earlier run (cancelled, or the app was killed) may still hold apk's lock.
        val leftovers = platform.killJobProcesses()
        if (leftovers > 0) log.line("killed $leftovers leftover install process(es) before starting")
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
            val available = platform.freeBytes(paths.runtimeDir)
            log.line("free space: ${mb(available)} MB, needed ${mb(needed)} MB")
            if (available < needed) {
                throw InstallException(
                    RuntimeErrorKind.NotEnoughSpace,
                    "Not enough storage: ${mb(needed)} MB of free space is needed, ${mb(available)} MB is available. Free up space and tap Install again.",
                )
            }
        }
        step(InstallStep.Download, skipIfDone = true) {
            log.line("downloading ${arch.url}")
            platform.download(arch.url, archive, arch.size) { onProgress(InstallStep.Download, it, arch.fileName) }
        }
        step(InstallStep.Verify, skipIfDone = true) {
            val actual = platform.sha256(archive)
            log.line("sha256 $actual (expected ${arch.sha256})")
            if (actual != arch.sha256) {
                archive.delete()
                journal.invalidateFrom(InstallStep.Download)
                throw InstallException(RuntimeErrorKind.Checksum, "Checksum mismatch for ${arch.fileName}. The file was deleted; tap Install to download it again.")
            }
        }
        step(InstallStep.Extract, skipIfDone = true) {
            // A partial extraction cannot be trusted; start from an empty directory.
            paths.rootfs.deleteRecursively()
            platform.extract(archive, paths.rootfs) { onProgress(InstallStep.Extract, it, null) }
        }
        step(InstallStep.Configure) { configure() }
        step(InstallStep.InstallPackages) { installPackages(mode == InstallMode.Repair) }
        step(InstallStep.VerifyToolchain) { verifyToolchain() }

        archive.delete()
        paths.readyMarker.writeText(AlpineRelease.VERSION)
        log.line("runtime ready")
    }

    private suspend fun step(step: InstallStep, skipIfDone: Boolean = false, block: suspend () -> Unit) {
        currentCoroutineContext().ensureActive()
        if (skipIfDone && journal.isDone(step)) {
            log.line("step $step: already done, skipped")
            return
        }
        log.line("step $step: start")
        onProgress(step, 0f, null)
        block()
        journal.markDone(step)
        log.line("step $step: done")
        onProgress(step, 1f, null)
    }

    private fun configure() {
        val root = paths.rootfs
        File(root, "etc/apk").mkdirs()
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
        // --wait: a briefly held lock (e.g. the user's own apk in a terminal) waits instead of failing.
        val apk = "apk --wait $APK_LOCK_WAIT_SECONDS --no-progress"
        val fix = if (repair) "$apk fix && " else ""
        val script = "set -e; $apk update && ${fix}$apk add ${AlpineRelease.PACKAGES.joinToString(" ")}"
        val progress = Regex("""^\((\d+)/(\d+)\)\s+(\S+)\s+(\S+)""")
        var output = execLogged(script) { line ->
            progress.find(line)?.let { m ->
                val (n, total, action, pkg) = m.destructured
                onProgress(InstallStep.InstallPackages, n.toFloat() / total.toFloat(), "$action $pkg")
            }
        }
        if (isLockFailure(output)) {
            // Something still held the lock for the whole wait: clean up once more and retry once.
            val killed = platform.killJobProcesses()
            log.line("apk database locked; killed $killed process(es); retrying once")
            output = execLogged(script) {}
            if (isLockFailure(output)) {
                throw InstallException(RuntimeErrorKind.Locked, "Another installation is still running and holds the package database lock.")
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
        val output = execLogged("clang --version | head -n 1 && python3 --version") {}
        if (output.exitCode != 0) {
            throw InstallException(RuntimeErrorKind.Verification, "Toolchain check failed:\n" + output.tail.takeLast(8).joinToString("\n"))
        }
    }

    private suspend fun execLogged(script: String, onLine: (String) -> Unit): ExecResult {
        log.line("$ $script")
        val result = platform.exec(script) { line ->
            log.line("  | $line")
            onLine(line)
        }
        log.line("exit ${result.exitCode}")
        return result
    }

    private fun mb(bytes: Long) = bytes / (1024 * 1024)

    companion object {
        const val APK_LOCK_WAIT_SECONDS = 60

        /** apk-tools 3 exits with 99 and prints this when the database lock cannot be taken. */
        fun isLockFailure(result: ExecResult): Boolean =
            result.exitCode != 0 && result.tail.any { "Unable to lock database" in it }
    }
}
