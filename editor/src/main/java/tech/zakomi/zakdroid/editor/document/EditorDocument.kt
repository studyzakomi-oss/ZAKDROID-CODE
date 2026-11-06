package tech.zakomi.zakdroid.editor.document

import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.editor.EditorLanguage

enum class LineEnding(val label: String, val sequence: String) { LF("LF", "\n"), CRLF("CRLF", "\r\n") }

/**
 * One open file. The sora [Content] holds the text, cursor and undo history, so
 * switching tabs keeps all three. Text is always LF in memory; [lineEnding] and
 * [hasBom] restore the original format on save.
 */
class EditorDocument(
    ref: FileRef,
    name: String,
    val language: EditorLanguage,
    val content: Content,
    val lineEnding: LineEnding,
    val hasBom: Boolean,
) {
    var ref: FileRef = ref
        private set
    var name: String = name
        private set

    private var savedVersion = content.documentVersion
    private var savedLength = content.length
    private var savedHash = if (savedLength <= HASH_LIMIT) contentHash(content.toString()) else 0L
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val listener = object : ContentListener {
        override fun beforeReplace(content: Content) = Unit
        override fun afterInsert(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, insertedContent: CharSequence) = refreshDirty()
        override fun afterDelete(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, deletedContent: CharSequence) = refreshDirty()
    }

    init {
        content.addContentListener(listener)
    }

    /**
     * Undo creates a new document version, so a version check alone keeps a file
     * "unsaved" after undoing back to the saved text. A length check rules out
     * almost every edit for free; only a same-length text is hashed. Very large
     * files skip the hash and fall back to the version check.
     */
    private fun refreshDirty() {
        _dirty.value = when {
            content.documentVersion == savedVersion -> false
            content.length != savedLength -> true
            savedLength > HASH_LIMIT -> true
            else -> contentHash(content.toString()) != savedHash
        }
    }

    /** Text and version to write to disk. Must be called on the main thread. */
    fun snapshotForSave(): SaveSnapshot {
        val text = content.toString()
        val disk = if (lineEnding == LineEnding.CRLF) text.replace("\n", "\r\n") else text
        return SaveSnapshot(content.documentVersion, text, disk)
    }

    /** Records [snapshot] as the text on disk. Must be called on the main thread. */
    fun markSaved(snapshot: SaveSnapshot) {
        savedVersion = snapshot.version
        savedLength = snapshot.text.length
        savedHash = if (savedLength <= HASH_LIMIT) contentHash(snapshot.text) else 0L
        refreshDirty()
    }

    fun moved(newRef: FileRef, newName: String) {
        ref = newRef
        name = newName
    }

    fun dispose() {
        content.removeContentListener(listener)
    }
}

/** What [EditorDocument.snapshotForSave] captured: [diskText] has the file's own line endings. */
class SaveSnapshot internal constructor(val version: Long, internal val text: String, val diskText: String)

private const val HASH_LIMIT = 1_000_000

/** 64-bit FNV-1a over the UTF-16 code units. */
internal fun contentHash(text: CharSequence): Long {
    var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    for (i in 0 until text.length) {
        h = (h xor text[i].code.toLong()) * 0x100000001b3L
    }
    return h
}
