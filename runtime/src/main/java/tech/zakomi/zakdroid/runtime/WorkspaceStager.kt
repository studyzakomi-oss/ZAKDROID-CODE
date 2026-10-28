package tech.zakomi.zakdroid.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.di.IoDispatcher
import tech.zakomi.zakdroid.core.fs.FileOperationException
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.core.fs.FileSystem
import tech.zakomi.zakdroid.core.workspace.StoragePaths
import tech.zakomi.zakdroid.core.workspace.Workspace
import tech.zakomi.zakdroid.core.workspace.WorkspaceKind
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps workspace files to paths inside the Linux runtime.
 *
 * Internal workspaces live under the bind-mounted home and are used in place.
 * SAF folders cannot be read by the toolchain, so they are mirrored into
 * `~/.zakdroid/staging/<id>` before each run (one-way, changed files only).
 */
@Singleton
class WorkspaceStager @Inject constructor(
    private val paths: StoragePaths,
    private val fs: FileSystem,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Returns the guest path of [file] inside [workspace], staging SAF workspaces first. */
    suspend fun prepare(workspace: Workspace, file: FileRef, relativePath: String): String = withContext(io) {
        when (workspace.kind) {
            WorkspaceKind.Internal -> toGuest(File((file as FileRef.Local).path))
            WorkspaceKind.Saf -> {
                val dir = File(paths.stagingDir, stagingName(workspace))
                sync(workspace.root, dir)
                toGuest(File(dir, relativePath))
            }
        }
    }

    private fun stagingName(workspace: Workspace): String {
        val safe = workspace.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        return "$safe-" + Integer.toHexString(workspace.id.hashCode())
    }

    private fun toGuest(file: File): String {
        val home = paths.homeDir.canonicalFile
        val canonical = file.canonicalFile
        require(canonical.path.startsWith(home.path)) { "$file is outside the runtime home" }
        return StoragePaths.GUEST_HOME + canonical.path.removePrefix(home.path)
    }

    private suspend fun sync(root: FileRef, target: File) {
        target.mkdirs()
        val seen = HashSet<String>()
        var files = 0
        var bytes = 0L

        suspend fun walk(dir: FileRef, local: File) {
            for (entry in fs.list(dir)) {
                if (entry.isDirectory && entry.name in SKIPPED_DIRS) continue
                val dest = File(local, entry.name)
                seen += dest.canonicalPath
                if (entry.isDirectory) {
                    dest.mkdirs()
                    walk(entry.ref, dest)
                } else {
                    files++
                    bytes += entry.size
                    if (files > MAX_FILES || bytes > MAX_BYTES) {
                        throw FileOperationException("Folder is too large to run from shared storage (limit $MAX_FILES files / ${MAX_BYTES / 1024 / 1024} MB)")
                    }
                    if (!dest.exists() || dest.length() != entry.size || dest.lastModified() < entry.lastModified) {
                        dest.writeBytes(fs.read(entry.ref))
                        if (entry.lastModified > 0) dest.setLastModified(entry.lastModified)
                    }
                }
            }
        }
        walk(root, target)
        // Remove files deleted from the source since the last sync.
        target.walkBottomUp().filter { it != target && it.canonicalPath !in seen }.forEach { it.deleteRecursively() }
    }

    private companion object {
        val SKIPPED_DIRS = setOf(".git", "build", "node_modules", "__pycache__", ".venv", "venv")
        const val MAX_FILES = 2000
        const val MAX_BYTES = 64L * 1024 * 1024
    }
}
