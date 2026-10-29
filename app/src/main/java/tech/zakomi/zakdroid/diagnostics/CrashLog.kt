package tech.zakomi.zakdroid.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to `files/crashes/` (newest kept as `last-crash.txt`,
 * plus up to [MAX_FILES] timestamped copies), then lets the default handler crash the app.
 */
object CrashLog {
    private const val DIR = "crashes"
    private const val LAST = "last-crash.txt"
    private const val MAX_FILES = 5

    fun install(context: Context) {
        val dir = File(context.filesDir, DIR)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(dir, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The most recent crash report, or null if the app has not crashed. */
    fun lastCrash(context: Context): String? =
        File(File(context.filesDir, DIR), LAST).takeIf { it.isFile }?.readText()

    private fun write(dir: File, thread: Thread, error: Throwable) {
        dir.mkdirs()
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val report = "time: $time\nthread: ${thread.name}\n\n$trace"
        File(dir, LAST).writeText(report)
        File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(report)
        dir.listFiles { f -> f.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }
}
