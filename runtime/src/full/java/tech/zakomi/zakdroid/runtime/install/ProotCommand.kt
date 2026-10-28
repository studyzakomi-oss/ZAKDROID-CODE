package tech.zakomi.zakdroid.runtime.install

import tech.zakomi.zakdroid.core.workspace.StoragePaths

/**
 * Builds PRoot invocations. The guest runs as fake root (-0) so `apk` works and
 * users can install extra packages; /home/user is the app's home directory.
 */
class ProotCommand(private val paths: RuntimePaths) {

    /** Environment for the PRoot process itself (host side). */
    fun hostEnv(): List<String> = listOf(
        "PROOT_TMP_DIR=${paths.prootTmp.absolutePath}",
        "PROOT_LOADER=${paths.prootLoader.absolutePath}",
        "HOME=${paths.home.absolutePath}",
        "TMPDIR=${paths.prootTmp.absolutePath}",
        "LANG=C.UTF-8",
    )

    /** Full argv: proot options, then a clean guest environment, then [command]. */
    fun argv(guestWorkDir: String, command: List<String>): List<String> = buildList {
        add(paths.proot.absolutePath)
        add("--kill-on-exit")
        add("--link2symlink")
        add("-0")
        add("-r"); add(paths.rootfs.absolutePath)
        add("-b"); add("/dev")
        add("-b"); add("/proc")
        add("-b"); add("/sys")
        add("-b"); add("${paths.home.absolutePath}:${StoragePaths.GUEST_HOME}")
        add("-w"); add(guestWorkDir)
        add("/usr/bin/env"); add("-i")
        GUEST_ENV.forEach(::add)
        addAll(command)
    }

    companion object {
        val GUEST_ENV = listOf(
            "HOME=${StoragePaths.GUEST_HOME}",
            "USER=root",
            "LOGNAME=root",
            "SHELL=/bin/sh",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=C.UTF-8",
            "TMPDIR=/tmp",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        )
    }
}
