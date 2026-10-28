package tech.zakomi.zakdroid.runtime

enum class RunLanguage { C, Cpp, Python }

/** A single file to build (if needed) and run inside the Linux runtime. Paths are guest paths. */
data class RunTarget(
    val language: RunLanguage,
    val fileName: String,
    val guestFile: String,
    val guestWorkDir: String,
)
