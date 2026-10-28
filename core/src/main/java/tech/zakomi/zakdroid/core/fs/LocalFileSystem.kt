package tech.zakomi.zakdroid.core.fs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.di.IoDispatcher
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Plain java.io.File backend, used for internal workspaces in app storage. */
@Singleton
class LocalFileSystem @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : FileSystem {

    override suspend fun list(dir: FileRef): List<FileEntry> = withContext(io) {
        val files = dir.file().listFiles() ?: throw FileOperationException("Cannot list ${dir.file().name}")
        files.map { it.toEntry() }.sortedForExplorer()
    }

    override suspend fun stat(ref: FileRef): FileEntry? = withContext(io) {
        ref.file().takeIf { it.exists() }?.toEntry()
    }

    override suspend fun read(ref: FileRef, maxBytes: Long): ByteArray = withContext(io) {
        val file = ref.file()
        if (file.length() > maxBytes) throw FileOperationException("File is larger than ${maxBytes / 1024 / 1024} MB")
        file.readBytes()
    }

    override suspend fun write(ref: FileRef, bytes: ByteArray) = withContext(io) {
        val file = ref.file()
        // Write to a sibling temp file first so a crash never leaves a truncated file.
        val tmp = File(file.parentFile, ".${file.name}.zdtmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            file.writeBytes(bytes)
        }
    }

    override suspend fun createFile(parent: FileRef, name: String): FileRef = withContext(io) {
        val file = File(parent.file(), checkedName(name))
        if (!file.createNewFile()) throw FileOperationException("\"$name\" already exists")
        FileRef.Local(file.absolutePath)
    }

    override suspend fun createDirectory(parent: FileRef, name: String): FileRef = withContext(io) {
        val dir = File(parent.file(), checkedName(name))
        if (dir.exists()) throw FileOperationException("\"$name\" already exists")
        if (!dir.mkdirs()) throw FileOperationException("Cannot create \"$name\"")
        FileRef.Local(dir.absolutePath)
    }

    override suspend fun rename(ref: FileRef, newName: String): FileRef = withContext(io) {
        val file = ref.file()
        val target = File(file.parentFile, checkedName(newName))
        if (target.exists()) throw FileOperationException("\"$newName\" already exists")
        if (!file.renameTo(target)) throw FileOperationException("Cannot rename \"${file.name}\"")
        FileRef.Local(target.absolutePath)
    }

    override suspend fun delete(ref: FileRef) = withContext(io) {
        if (!ref.file().deleteRecursively()) throw FileOperationException("Cannot delete \"${ref.file().name}\"")
    }

    override suspend fun move(ref: FileRef, currentParent: FileRef, newParent: FileRef): FileRef = withContext(io) {
        val source = ref.file()
        val targetDir = newParent.file()
        val target = File(targetDir, source.name)
        if (target.exists()) throw FileOperationException("\"${source.name}\" already exists in ${targetDir.name}")
        if (targetDir.canonicalPath.startsWith(source.canonicalPath + File.separator) || targetDir == source) {
            throw FileOperationException("Cannot move a folder into itself")
        }
        if (!source.renameTo(target)) {
            try {
                source.copyRecursively(target)
                source.deleteRecursively()
            } catch (e: IOException) {
                throw FileOperationException("Cannot move \"${source.name}\"", e)
            }
        }
        FileRef.Local(target.absolutePath)
    }

    private fun FileRef.file(): File = when (this) {
        is FileRef.Local -> File(path)
        is FileRef.Saf -> throw IllegalArgumentException("Not a local file: $key")
    }

    private fun File.toEntry() = FileEntry(
        ref = FileRef.Local(absolutePath),
        name = name,
        isDirectory = isDirectory,
        size = if (isDirectory) 0 else length(),
        lastModified = lastModified(),
    )

    private fun checkedName(name: String): String {
        FileNames.validate(name)?.let { throw FileOperationException(it) }
        return name
    }
}
