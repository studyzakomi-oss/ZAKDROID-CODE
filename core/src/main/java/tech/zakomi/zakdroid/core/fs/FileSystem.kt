package tech.zakomi.zakdroid.core.fs

/** File operations shared by all backends. All functions are blocking-safe suspend calls. */
interface FileSystem {
    suspend fun list(dir: FileRef): List<FileEntry>
    suspend fun stat(ref: FileRef): FileEntry?
    suspend fun read(ref: FileRef, maxBytes: Long = Long.MAX_VALUE): ByteArray
    suspend fun write(ref: FileRef, bytes: ByteArray)
    suspend fun createFile(parent: FileRef, name: String): FileRef
    suspend fun createDirectory(parent: FileRef, name: String): FileRef
    suspend fun rename(ref: FileRef, newName: String): FileRef
    suspend fun delete(ref: FileRef)

    /** Moves [ref] (currently inside [currentParent]) into [newParent]. */
    suspend fun move(ref: FileRef, currentParent: FileRef, newParent: FileRef): FileRef
}

internal fun List<FileEntry>.sortedForExplorer(): List<FileEntry> =
    sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
