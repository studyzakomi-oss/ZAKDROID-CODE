package tech.zakomi.zakdroid.core.fs

import javax.inject.Inject
import javax.inject.Singleton

/** Routes each call to the backend that owns the [FileRef]. */
@Singleton
class WorkspaceFileSystem @Inject constructor(
    private val local: LocalFileSystem,
    private val saf: SafFileSystem,
) : FileSystem {

    private fun backend(ref: FileRef): FileSystem = when (ref) {
        is FileRef.Local -> local
        is FileRef.Saf -> saf
    }

    override suspend fun list(dir: FileRef) = backend(dir).list(dir)
    override suspend fun stat(ref: FileRef) = backend(ref).stat(ref)
    override suspend fun read(ref: FileRef, maxBytes: Long) = backend(ref).read(ref, maxBytes)
    override suspend fun write(ref: FileRef, bytes: ByteArray) = backend(ref).write(ref, bytes)
    override suspend fun createFile(parent: FileRef, name: String) = backend(parent).createFile(parent, name)
    override suspend fun createDirectory(parent: FileRef, name: String) = backend(parent).createDirectory(parent, name)
    override suspend fun rename(ref: FileRef, newName: String) = backend(ref).rename(ref, newName)
    override suspend fun delete(ref: FileRef) = backend(ref).delete(ref)
    override suspend fun move(ref: FileRef, currentParent: FileRef, newParent: FileRef): FileRef {
        require(ref::class == newParent::class) { "Cannot move between storage backends" }
        return backend(ref).move(ref, currentParent, newParent)
    }
}
