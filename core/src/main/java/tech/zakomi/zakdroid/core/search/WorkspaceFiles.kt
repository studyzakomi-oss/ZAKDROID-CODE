package tech.zakomi.zakdroid.core.search

import tech.zakomi.zakdroid.core.fs.FileEntry
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.core.fs.FileSystem

/** A file somewhere below the workspace root; [relativePath] uses '/' and has no leading slash. */
data class WorkspaceFile(val entry: FileEntry, val relativePath: String)

class WorkspaceListing(val files: List<WorkspaceFile>, /** True when the walk stopped at its limit. */ val truncated: Boolean)

object WorkspaceFiles {
    /** Folders that hold generated or third-party files, skipped like VS Code's default excludes. */
    val SKIPPED_DIRS = setOf(".git", ".svn", ".hg", "node_modules", "build", "__pycache__", ".venv", "venv", ".gradle", ".idea")

    const val DEFAULT_LIMIT = 10_000

    /** Lists every file below [root], depth-first in Explorer order. Unreadable folders are skipped. */
    suspend fun list(fs: FileSystem, root: FileRef, limit: Int = DEFAULT_LIMIT): WorkspaceListing {
        val out = ArrayList<WorkspaceFile>()
        var truncated = false
        suspend fun walk(dir: FileRef, prefix: String) {
            val entries = try {
                fs.list(dir)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
            for (entry in entries) {
                if (truncated) return
                val relative = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                if (entry.isDirectory) {
                    if (entry.name !in SKIPPED_DIRS) walk(entry.ref, relative)
                } else if (out.size >= limit) {
                    truncated = true
                } else {
                    out += WorkspaceFile(entry, relative)
                }
            }
        }
        walk(root, "")
        return WorkspaceListing(out, truncated)
    }
}
