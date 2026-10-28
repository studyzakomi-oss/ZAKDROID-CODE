package tech.zakomi.zakdroid.runtime

import kotlinx.coroutines.flow.StateFlow
import tech.zakomi.zakdroid.terminal.ShellSpec

/**
 * The toolchain environment. The `full` flavor provides a local PRoot + Alpine
 * runtime; Phase 4's `play` flavor provides a remote (SSH) one behind the same API.
 */
interface RuntimeManager {
    val state: StateFlow<RuntimeState>

    /** Human-readable description, e.g. "Alpine Linux 3.24.3 (aarch64) · PRoot 5.1.107.101". */
    val description: String

    /** Packages installed by [install]. */
    val packages: List<String>

    /** Starts the install, or resumes a cancelled/failed one from its last completed step. */
    fun install()

    fun cancel()

    /** Re-applies configuration and reinstalls missing or broken packages. Keeps user files. */
    fun repair()

    /** Deletes the Linux system and installs it again. Projects in /home/user are kept. */
    fun reinstall()

    /** An interactive login shell. Requires [RuntimeState.Ready]. */
    fun shell(title: String, guestWorkDir: String): ShellSpec

    /** Builds (C/C++) and runs a file, printing exit code and timings. Requires [RuntimeState.Ready]. */
    fun run(target: RunTarget): ShellSpec
}
