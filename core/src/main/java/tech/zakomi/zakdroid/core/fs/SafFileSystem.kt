package tech.zakomi.zakdroid.core.fs

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.di.IoDispatcher
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage Access Framework backend for folders opened with "Open folder".
 * Talks to DocumentsContract directly: one query per directory listing instead
 * of DocumentFile's one query per child attribute.
 */
@Singleton
class SafFileSystem @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : FileSystem {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun list(dir: FileRef): List<FileEntry> = withContext(io) {
        val dirUri = dir.uri()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, DocumentsContract.getDocumentId(dirUri))
        val entries = mutableListOf<FileEntry>()
        resolver.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val childUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, c.getString(0))
                entries += c.toEntry(childUri)
            }
        } ?: throw FileOperationException("Cannot list folder")
        entries.sortedForExplorer()
    }

    override suspend fun stat(ref: FileRef): FileEntry? = withContext(io) {
        try {
            resolver.query(ref.uri(), PROJECTION, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.toEntry(ref.uri()) else null
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun read(ref: FileRef, maxBytes: Long): ByteArray = withContext(io) {
        val size = stat(ref)?.size ?: 0
        if (size > maxBytes) throw FileOperationException("File is larger than ${maxBytes / 1024 / 1024} MB")
        resolver.openInputStream(ref.uri())?.use { it.readBytes() }
            ?: throw FileOperationException("Cannot open file")
    }

    override suspend fun write(ref: FileRef, bytes: ByteArray) = withContext(io) {
        val stream = try {
            resolver.openOutputStream(ref.uri(), "wt")
        } catch (_: IllegalArgumentException) {
            // Some providers do not support truncate mode; "w" truncates on most of them.
            resolver.openOutputStream(ref.uri(), "w")
        } ?: throw FileOperationException("Cannot write file")
        stream.use { it.write(bytes) }
    }

    override suspend fun createFile(parent: FileRef, name: String): FileRef =
        create(parent, name, OCTET_STREAM)

    override suspend fun createDirectory(parent: FileRef, name: String): FileRef =
        create(parent, name, Document.MIME_TYPE_DIR)

    override suspend fun rename(ref: FileRef, newName: String): FileRef = withContext(io) {
        FileNames.validate(newName)?.let { throw FileOperationException(it) }
        val renamed = guard("Cannot rename") { DocumentsContract.renameDocument(resolver, ref.uri(), newName) }
        renamed?.let { FileRef.Saf(it.toString()) } ?: ref
    }

    override suspend fun delete(ref: FileRef) = withContext(io) {
        val ok = guard("Cannot delete") { DocumentsContract.deleteDocument(resolver, ref.uri()) }
        if (!ok) throw FileOperationException("Cannot delete")
    }

    override suspend fun move(ref: FileRef, currentParent: FileRef, newParent: FileRef): FileRef = withContext(io) {
        val moved = try {
            DocumentsContract.moveDocument(resolver, ref.uri(), currentParent.uri(), newParent.uri())
        } catch (_: UnsupportedOperationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (moved != null) return@withContext FileRef.Saf(moved.toString())
        // Provider without move support: copy, then delete the source.
        val copy = copyRecursively(ref, newParent)
        delete(ref)
        copy
    }

    private suspend fun copyRecursively(source: FileRef, targetParent: FileRef): FileRef {
        val entry = stat(source) ?: throw FileOperationException("Source no longer exists")
        return if (entry.isDirectory) {
            val dir = createDirectory(targetParent, entry.name)
            list(source).forEach { copyRecursively(it.ref, dir) }
            dir
        } else {
            val file = createFile(targetParent, entry.name)
            write(file, read(source))
            file
        }
    }

    private suspend fun create(parent: FileRef, name: String, mime: String): FileRef = withContext(io) {
        FileNames.validate(name)?.let { throw FileOperationException(it) }
        if (list(parent).any { it.name == name }) throw FileOperationException("\"$name\" already exists")
        val uri = guard("Cannot create \"$name\"") { DocumentsContract.createDocument(resolver, parent.uri(), mime, name) }
            ?: throw FileOperationException("Cannot create \"$name\"")
        FileRef.Saf(uri.toString())
    }

    private inline fun <T> guard(message: String, block: () -> T): T = try {
        block()
    } catch (e: FileOperationException) {
        throw e
    } catch (e: Exception) {
        throw FileOperationException("$message: ${e.message ?: e.javaClass.simpleName}", e)
    }

    private fun Cursor.toEntry(uri: Uri): FileEntry {
        val mime = getString(2)
        val isDir = mime == Document.MIME_TYPE_DIR
        return FileEntry(
            ref = FileRef.Saf(uri.toString()),
            name = getString(1) ?: "?",
            isDirectory = isDir,
            size = if (isDir || isNull(3)) 0 else getLong(3),
            lastModified = if (isNull(4)) 0 else getLong(4),
        )
    }

    private fun FileRef.uri(): Uri = when (this) {
        is FileRef.Saf -> Uri.parse(uri)
        is FileRef.Local -> throw IllegalArgumentException("Not a SAF document: $key")
    }

    companion object {
        private const val OCTET_STREAM = "application/octet-stream"
        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

        /** Converts a tree URI returned by ACTION_OPEN_DOCUMENT_TREE to its root document. */
        fun rootDocument(treeUri: Uri): FileRef.Saf =
            FileRef.Saf(DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)).toString())
    }
}
