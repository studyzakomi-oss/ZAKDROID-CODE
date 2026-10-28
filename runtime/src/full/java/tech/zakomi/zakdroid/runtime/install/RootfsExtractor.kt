package tech.zakomi.zakdroid.runtime.install

import android.system.ErrnoException
import android.system.Os
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import tech.zakomi.zakdroid.runtime.RuntimeErrorKind
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Extracts the Alpine minirootfs tarball, keeping modes and symlinks. */
object RootfsExtractor {

    suspend fun extract(archive: File, target: File, onProgress: (Float) -> Unit) {
        val root = target.canonicalFile
        root.mkdirs()
        val total = archive.length().toFloat()
        val hardLinks = mutableListOf<Pair<File, File>>()
        try {
            val counting = CountingInputStream(archive.inputStream().buffered())
            TarArchiveInputStream(GzipCompressorInputStream(counting)).use { tar ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = tar.nextEntry ?: break
                    // Resolve lexically: the archive contains absolute symlinks that must not be followed.
                    val segments = entry.name.split('/').filter { it.isNotEmpty() && it != "." }
                    if (segments.any { it == ".." }) throw IOException("Unsafe path in archive: ${entry.name}")
                    if (segments.isEmpty()) continue
                    val dest = File(root, segments.joinToString("/"))
                    when {
                        entry.isDirectory -> {
                            dest.mkdirs()
                            chmod(dest, entry.mode)
                        }
                        entry.isSymbolicLink -> {
                            dest.parentFile?.mkdirs()
                            dest.delete()
                            Os.symlink(entry.linkName, dest.path)
                        }
                        entry.isLink -> hardLinks += dest to File(root, entry.linkName)
                        entry.isFile -> {
                            dest.parentFile?.mkdirs()
                            FileOutputStream(dest).use { out -> tar.copyTo(out) }
                            chmod(dest, entry.mode)
                        }
                    }
                    onProgress(counting.count / total)
                }
            }
            // Android app storage may refuse hard links; copies behave the same for a rootfs.
            hardLinks.forEach { (link, source) ->
                link.parentFile?.mkdirs()
                source.copyTo(link, overwrite = true)
                if (source.canExecute()) link.setExecutable(true, false)
            }
        } catch (e: IOException) {
            throw InstallException(RuntimeErrorKind.Extract, "Cannot extract the Linux system: ${e.message}", e)
        } catch (e: ErrnoException) {
            throw InstallException(RuntimeErrorKind.Extract, "Cannot extract the Linux system: ${e.message}", e)
        }
    }

    private fun chmod(file: File, mode: Int) {
        try {
            Os.chmod(file.path, mode and 0x1FF or 0x180) // keep owner read/write
        } catch (_: ErrnoException) {
        }
    }

    private class CountingInputStream(private val source: java.io.InputStream) : java.io.FilterInputStream(source) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }
}
