package tech.zakomi.zakdroid.editor.document

import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.fs.FileEntry
import tech.zakomi.zakdroid.core.fs.FileSystem
import tech.zakomi.zakdroid.editor.EditorLanguage
import tech.zakomi.zakdroid.editor.textmate.TextMateSetup
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoadResult {
    data class Loaded(val document: EditorDocument) : LoadResult
    data class Failed(val reason: Reason, val message: String) : LoadResult
    enum class Reason { TooLarge, Binary, NotUtf8, IoError }
}

/** Reads and writes [EditorDocument]s. UTF-8 only in Phase 1. */
@Singleton
class DocumentLoader @Inject constructor(
    private val fs: FileSystem,
    private val textMate: TextMateSetup,
) {
    suspend fun load(entry: FileEntry): LoadResult {
        val bytes = try {
            fs.read(entry.ref, MAX_FILE_BYTES)
        } catch (e: Exception) {
            val tooLarge = entry.size > MAX_FILE_BYTES
            return LoadResult.Failed(
                if (tooLarge) LoadResult.Reason.TooLarge else LoadResult.Reason.IoError,
                e.message ?: "Cannot read file",
            )
        }
        val decoded = withContext(Dispatchers.Default) { decode(bytes) } ?: return LoadResult.Failed(
            if (looksBinary(bytes)) LoadResult.Reason.Binary else LoadResult.Reason.NotUtf8,
            "This file is not UTF-8 text",
        )
        val language = EditorLanguage.fromFileName(entry.name)
        textMate.preload(language)
        // Building the line index of a large file is the expensive part; keep it off the UI thread.
        val content = withContext(Dispatchers.Default) { Content(decoded.text) }
        return LoadResult.Loaded(
            EditorDocument(entry.ref, entry.name, language, content, decoded.lineEnding, decoded.hasBom),
        )
    }

    /** Writes the document and returns once it is on disk. Call from the main thread. */
    suspend fun save(document: EditorDocument) {
        val version = document.content.documentVersion
        val text = document.snapshotForSave()
        val bytes = withContext(Dispatchers.Default) {
            val body = text.toByteArray(Charsets.UTF_8)
            if (document.hasBom) UTF8_BOM + body else body
        }
        fs.write(document.ref, bytes)
        document.markSaved(version)
    }

    private class Decoded(val text: String, val lineEnding: LineEnding, val hasBom: Boolean)

    private fun decode(bytes: ByteArray): Decoded? {
        if (looksBinary(bytes)) return null
        val hasBom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val offset = if (hasBom) 3 else 0
        val raw = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        val crlf = raw.indexOf("\r\n").let { it >= 0 && it <= raw.indexOf('\n') }
        val text = if (raw.contains('\r')) raw.replace("\r\n", "\n").replace('\r', '\n') else raw
        return Decoded(text, if (crlf) LineEnding.CRLF else LineEnding.LF, hasBom)
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, 8192)
        for (i in 0 until limit) if (bytes[i] == 0.toByte()) return true
        return false
    }

    companion object {
        const val MAX_FILE_BYTES: Long = 8L * 1024 * 1024
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
