package tech.zakomi.zakdroid.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.zakomi.zakdroid.core.di.ApplicationScope
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.editor.document.EditorDocument
import javax.inject.Inject
import javax.inject.Singleton

data class OpenTab(
    val key: String,
    val name: String,
    val relativePath: String,
    val dirty: Boolean,
    val document: EditorDocument,
)

/**
 * Open editor documents, in tab order. Shared by the editor (tabs) and the
 * explorer (renames and deletes must reach open files). Main thread only.
 */
@Singleton
class OpenDocumentsStore @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val _tabs = MutableStateFlow<List<OpenTab>>(emptyList())
    val tabs: StateFlow<List<OpenTab>> = _tabs.asStateFlow()

    private val _activeKey = MutableStateFlow<String?>(null)
    val activeKey: StateFlow<String?> = _activeKey.asStateFlow()

    private val dirtyWatchers = HashMap<String, Job>()

    val active: OpenTab? get() = _tabs.value.firstOrNull { it.key == _activeKey.value }

    fun find(key: String): OpenTab? = _tabs.value.firstOrNull { it.key == key }

    fun add(document: EditorDocument, relativePath: String, activate: Boolean = true) {
        val key = document.ref.key
        if (find(key) == null) {
            _tabs.update { it + OpenTab(key, document.name, relativePath, false, document) }
            watchDirty(key, document)
        }
        if (activate) _activeKey.value = key
    }

    fun activate(key: String) {
        if (find(key) != null) _activeKey.value = key
    }

    fun activateOffset(offset: Int) {
        val list = _tabs.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.key == _activeKey.value }.coerceAtLeast(0)
        _activeKey.value = list[(index + offset).mod(list.size)].key
    }

    fun remove(key: String) {
        val list = _tabs.value
        val index = list.indexOfFirst { it.key == key }
        if (index < 0) return
        list[index].document.dispose()
        dirtyWatchers.remove(key)?.cancel()
        val remaining = list.filterIndexed { i, _ -> i != index }
        _tabs.value = remaining
        if (_activeKey.value == key) {
            _activeKey.value = remaining.getOrNull(index.coerceAtMost(remaining.size - 1))?.key
        }
    }

    fun clear() {
        _tabs.value.forEach { remove(it.key) }
    }

    /** Tabs whose file is [relativePath] or lies inside that folder. */
    fun under(relativePath: String): List<OpenTab> =
        _tabs.value.filter { it.relativePath == relativePath || it.relativePath.startsWith("$relativePath/") }

    fun onFileMoved(oldKey: String, newRef: FileRef, newName: String, newRelativePath: String) {
        val tab = find(oldKey) ?: return
        tab.document.moved(newRef, newName)
        dirtyWatchers.remove(oldKey)?.cancel()
        _tabs.update { list ->
            list.map { if (it.key == oldKey) it.copy(key = newRef.key, name = newName, relativePath = newRelativePath) else it }
        }
        if (_activeKey.value == oldKey) _activeKey.value = newRef.key
        watchDirty(newRef.key, tab.document)
    }

    private fun watchDirty(key: String, document: EditorDocument) {
        dirtyWatchers[key] = appScope.launch(Dispatchers.Main.immediate) {
            document.dirty.collect { dirty ->
                _tabs.update { list -> list.map { if (it.key == key) it.copy(dirty = dirty) else it } }
            }
        }
    }
}
