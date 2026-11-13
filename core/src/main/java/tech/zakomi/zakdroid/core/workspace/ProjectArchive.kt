package tech.zakomi.zakdroid.core.workspace

import tech.zakomi.zakdroid.core.fs.FileNames
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Packs in-app projects into a zip archive and unpacks them again, so they
 * survive an uninstall. Plain java.io, so it is unit-tested on the JVM.
 *
 * Archive layout: one top-level folder per project. A zip whose top level has
 * anything but folders (for example a folder zipped from inside) is imported
 * as a single project.
 */
object ProjectArchive {
    const val MAX_FILES = 50_000
    const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024

    /**
     * Writes [projects] to [out] and returns the number of files written.
     * Symbolic links are skipped: a zip entry cannot carry one safely.
     */
    fun export(projects: List<File>, out: OutputStream): Int {
        var files = 0
        ZipOutputStream(out).use { zip ->
            fun add(file: File, path: String) {
                if (Files.isSymbolicLink(file.toPath())) return
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$path/").apply { time = file.lastModified() })
                    zip.closeEntry()
                    file.listFiles().orEmpty().sortedBy { it.name }.forEach { add(it, "$path/${it.name}") }
                } else if (file.isFile) {
                    zip.putNextEntry(ZipEntry(path).apply { time = file.lastModified() })
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    files++
                }
            }
            for (project in projects) {
                require(project.isDirectory) { "Not a project folder: ${project.name}" }
                add(project, project.name)
            }
        }
        return files
    }

    /**
     * Unpacks [zip] and moves its projects into [projectsDir]; returns their folders.
     * A project whose name is taken gets a numeric suffix, so nothing is overwritten.
     * [scratchDir] must be on the same file system as [projectsDir]. Nothing is left
     * behind when the archive is rejected.
     */
    fun import(zip: InputStream, projectsDir: File, scratchDir: File, fallbackName: String): List<File> {
        val staging = File(scratchDir, "import-${System.nanoTime()}")
        try {
            unpack(zip, staging)
            val top = staging.listFiles().orEmpty().sortedBy { it.name }
            val sources = if (top.all { it.isDirectory }) top.map { it to it.name } else listOf(staging to fallbackName)
            sources.forEach { (_, name) -> FileNames.validate(name)?.let { throw IllegalArgumentException("Invalid project name \"$name\": $it") } }
            projectsDir.mkdirs()
            return sources.map { (dir, name) ->
                val target = freeName(projectsDir, name)
                if (!dir.renameTo(target)) {
                    dir.copyRecursively(target, overwrite = false)
                }
                target
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun freeName(parent: File, name: String): File {
        var candidate = File(parent, name)
        var n = 2
        while (candidate.exists()) candidate = File(parent, "$name-${n++}")
        return candidate
    }

    private fun unpack(zip: InputStream, into: File) {
        into.mkdirs()
        val canonicalRoot = into.canonicalPath + File.separator
        var files = 0
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        ZipInputStream(zip).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val target = File(into, entry.name.replace('\\', '/'))
                val canonical = target.canonicalPath
                require(canonical.startsWith(canonicalRoot) || canonical + File.separator == canonicalRoot) {
                    "The archive contains an unsafe path: ${entry.name}"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                require(++files <= MAX_FILES) { "The archive has more than $MAX_FILES files" }
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_TOTAL_BYTES) { "The archive is larger than ${MAX_TOTAL_BYTES / 1024 / 1024} MB when unpacked" }
                        out.write(buffer, 0, n)
                    }
                }
                if (entry.time > 0) target.setLastModified(entry.time)
            }
        }
        require(into.listFiles().orEmpty().isNotEmpty()) { "The file is not a zip archive, or it is empty" }
    }
}
