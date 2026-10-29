package tech.zakomi.zakdroid.runtime.install

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full log of installer runs: steps, every line of `apk` output, errors.
 * Each run starts a fresh `install.log`; the previous run is kept as `install.previous.log`.
 */
class InstallLog(private val current: File, private val previous: File) {
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun startRun(header: String) {
        current.parentFile?.mkdirs()
        if (current.exists()) current.renameTo(previous.also { it.delete() })
        current.writeText("")
        line("==== $header")
    }

    @Synchronized
    fun line(text: String) {
        runCatching { current.appendText("${time.format(Date())} $text\n") }
    }

    /** The current and previous runs, newest last, capped at [maxChars] (oldest text dropped). */
    fun tail(maxChars: Int = 48_000): String? {
        val text = buildString {
            if (previous.isFile) append("--- previous run ---\n").append(previous.readText())
            if (current.isFile) append("--- latest run ---\n").append(current.readText())
        }
        if (text.isEmpty()) return null
        return if (text.length <= maxChars) text else "…(truncated)\n" + text.takeLast(maxChars)
    }
}
