package tech.zakomi.zakdroid.core.fs

/**
 * A file or folder location. Two backends exist: plain paths in app storage
 * ([Local]) and Storage Access Framework documents ([Saf]).
 * [key] is stable and is what gets persisted.
 */
sealed interface FileRef {
    val key: String

    data class Local(val path: String) : FileRef {
        override val key: String get() = LOCAL_PREFIX + path
    }

    data class Saf(val uri: String) : FileRef {
        override val key: String get() = uri
    }

    companion object {
        private const val LOCAL_PREFIX = "file:"

        fun fromKey(key: String): FileRef =
            if (key.startsWith(LOCAL_PREFIX)) Local(key.removePrefix(LOCAL_PREFIX)) else Saf(key)
    }
}

data class FileEntry(
    val ref: FileRef,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)

class FileOperationException(message: String, cause: Throwable? = null) : Exception(message, cause)

object FileNames {
    private val FORBIDDEN = charArrayOf('/', '\u0000')

    /** Returns an error message, or null if [name] is a valid single path segment. */
    fun validate(name: String): String? = when {
        name.isBlank() -> "Name must not be empty"
        name == "." || name == ".." -> "\"$name\" is reserved"
        name.any { it in FORBIDDEN } -> "Name must not contain '/'"
        name.length > 255 -> "Name is too long"
        else -> null
    }

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()
}
