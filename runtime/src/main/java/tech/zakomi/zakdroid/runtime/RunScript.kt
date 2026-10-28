package tech.zakomi.zakdroid.runtime

/**
 * Builds the POSIX sh script for a one-tap run. Output ends with the exit code
 * and the execution time. Timing uses /proc/uptime (10 ms resolution) with a
 * 1 s `date` fallback where /proc/uptime is not readable.
 */
object RunScript {
    /** Compiler flags. AddressSanitizer is deliberately absent: it is poorly supported on musl. */
    val C_FLAGS = listOf("-g", "-Wall", "-Wextra", "-std=c17")
    val CPP_FLAGS = listOf("-g", "-Wall", "-Wextra", "-std=c++20")

    fun build(target: RunTarget): String {
        val src = quote(target.guestFile)
        val outDir = "/tmp/zakdroid-run/" + Integer.toHexString(target.guestFile.hashCode())
        val out = quote("$outDir/" + target.fileName.substringBeforeLast('.').ifEmpty { "a.out" })
        val name = quote(target.fileName)
        val header = """
            |now() { if [ -r /proc/uptime ]; then cut -d' ' -f1 /proc/uptime; else date +%s; fi; }
            |since() { awk -v a="${'$'}1" -v b="${'$'}(now)" 'BEGIN { printf "%.2f", b - a }'; }
            |footer() {
            |  if [ "${'$'}1" -eq 0 ]; then color=32; else color=31; fi
            |  printf '\n\033[1;%sm■ %s exited with code %d · %s s\033[0m\n' "${'$'}color" ${name} "${'$'}1" "${'$'}2"
            |}
            |""".trimMargin()
        val build = when (target.language) {
            RunLanguage.Python -> ""
            RunLanguage.C, RunLanguage.Cpp -> {
                val compiler = if (target.language == RunLanguage.C) "clang" else "clang++"
                val flags = (if (target.language == RunLanguage.C) C_FLAGS else CPP_FLAGS).joinToString(" ")
                val libs = if (target.language == RunLanguage.C) " -lm" else ""
                """
                |mkdir -p ${quote(outDir)} || exit 1
                |printf '\033[1;34m▶ %s %s\033[0m\n' $compiler $name
                |t0=${'$'}(now)
                |$compiler $flags -o $out $src$libs
                |status=${'$'}?
                |took=${'$'}(since "${'$'}t0")
                |if [ "${'$'}status" -ne 0 ]; then
                |  printf '\n\033[1;31m✖ Build failed with exit code %d · %s s\033[0m\n' "${'$'}status" "${'$'}took"
                |  exit "${'$'}status"
                |fi
                |printf '\033[2m  built in %s s\033[0m\n\n' "${'$'}took"
                |""".trimMargin()
            }
        }
        val runCommand = when (target.language) {
            RunLanguage.Python -> "python3 -u $src"
            RunLanguage.C, RunLanguage.Cpp -> out
        }
        val run = """
            |t0=${'$'}(now)
            |$runCommand
            |status=${'$'}?
            |footer "${'$'}status" "${'$'}(since "${'$'}t0")"
            |exit "${'$'}status"
            |""".trimMargin()
        return listOf(header, build, run).filter { it.isNotEmpty() }.joinToString("\n")
    }

    /** Single-quotes [value] for POSIX sh. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
