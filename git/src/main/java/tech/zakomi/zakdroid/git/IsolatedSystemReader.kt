package tech.zakomi.zakdroid.git

import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader

/**
 * Makes JGit ignore Git settings outside the repository (`~/.gitconfig`, `/etc/gitconfig`).
 * On Android there is no such file to rely on, and a setting this JGit version does not
 * know (for example `gpg.format=ssh`) would otherwise fail every operation.
 */
internal class IsolatedSystemReader(private val base: SystemReader) : SystemReader() {

    private class EmptyConfig(parent: Config?, fs: FS) : FileBasedConfig(parent, null, fs) {
        override fun load() = Unit
        override fun save() = Unit
        override fun isOutdated() = false
    }

    override fun getHostname(): String = base.hostname
    override fun getenv(variable: String): String? = if (variable.startsWith("GIT_")) null else base.getenv(variable)
    override fun getProperty(key: String): String? = base.getProperty(key)
    override fun openUserConfig(parent: Config?, fs: FS): FileBasedConfig = EmptyConfig(parent, fs)
    override fun openSystemConfig(parent: Config?, fs: FS): FileBasedConfig = EmptyConfig(parent, fs)
    override fun openJGitConfig(parent: Config?, fs: FS): FileBasedConfig = EmptyConfig(parent, fs)
    override fun getCurrentTime(): Long = base.currentTime
    override fun getTimezone(time: Long): Int = base.getTimezone(time)

    companion object {
        fun install() {
            val current = getInstance()
            if (current !is IsolatedSystemReader) setInstance(IsolatedSystemReader(current))
        }
    }
}
