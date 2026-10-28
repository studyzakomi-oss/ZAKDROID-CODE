package tech.zakomi.zakdroid.runtime.install

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.zakomi.zakdroid.runtime.RuntimeErrorKind
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Resumable HTTPS download into `<target>.part`, renamed to [target] when complete. */
class Downloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun download(url: String, target: File, expectedSize: Long, onProgress: (Float) -> Unit) {
        if (target.exists() && target.length() == expectedSize) return
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        if (part.length() > expectedSize) part.delete()

        val request = Request.Builder().url(url).apply {
            if (part.length() > 0) header("Range", "bytes=${part.length()}-")
        }.build()
        try {
            client.newCall(request).execute().use { response ->
                val append = response.code == 206
                if (!response.isSuccessful) {
                    throw InstallException(RuntimeErrorKind.Network, "Download failed: HTTP ${response.code} for $url")
                }
                val body = response.body
                FileOutputStream(part, append).use { out ->
                    var done = if (append) part.length() else 0L
                    val buffer = ByteArray(64 * 1024)
                    body.byteStream().use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            done += read
                            onProgress(done.toFloat() / expectedSize)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw InstallException(RuntimeErrorKind.Network, "Download interrupted (${e.message ?: "network error"}). Check your connection and tap Install to resume.", e)
        }
        if (part.length() != expectedSize) {
            throw InstallException(RuntimeErrorKind.Network, "Download incomplete: ${part.length()} of $expectedSize bytes. Tap Install to resume.")
        }
        if (!part.renameTo(target)) throw InstallException(RuntimeErrorKind.Unknown, "Cannot finalize download")
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
