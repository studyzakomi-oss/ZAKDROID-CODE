package tech.zakomi.zakdroid.runtime.install

import java.io.File

class ExecResult(val exitCode: Int, val tail: List<String>)

/**
 * Everything the installer needs from Android, the network and PRoot. The production
 * implementation is [AndroidInstallPlatform]; unit tests use a fake.
 */
interface InstallPlatform {
    fun deviceArch(): AlpineRelease.Arch?

    /** Throws [InstallException] when PRoot is missing from the APK. */
    fun checkPrerequisites()

    fun freeBytes(dir: File): Long

    suspend fun download(url: String, target: File, expectedSize: Long, onProgress: (Float) -> Unit)

    fun sha256(file: File): String

    suspend fun extract(archive: File, target: File, onProgress: (Float) -> Unit)

    /**
     * Runs [script] with /bin/sh inside the rootfs. All processes of the job are tagged;
     * they are killed when the call returns or its coroutine is cancelled.
     */
    suspend fun exec(script: String, onLine: (String) -> Unit): ExecResult

    /** Kills every process left from an install job (including orphans of earlier runs). Returns the count. */
    fun killJobProcesses(): Int

    /** PIDs of the running job's processes (for the lock file / diagnostics). */
    fun jobPids(): List<Int>

    /**
     * Resolves [host] with Android's resolver (honours Private DNS and the active network).
     * IPv4 addresses first. Empty if the name cannot be resolved (e.g. no internet).
     */
    suspend fun resolve(host: String): List<String>

    /** DNS servers of the active network, for the rootfs resolv.conf. */
    fun dnsServers(): List<String>
}
