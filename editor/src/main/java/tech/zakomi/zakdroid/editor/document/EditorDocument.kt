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

    private fun refreshDirty() {
        _dirty.value = content.documentVersion != savedVersion
    }

    /** Text as it should be written to disk. Must be called on the main thread. */
    fun snapshotForSave(): String {
        val text = content.toString()
        return if (lineEnding == LineEnding.CRLF) text.replace("\n", "\r\n") else text
    }

    fun markSaved(versionAtSnapshot: Long) {
        savedVersion = versionAtSnapshot
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
