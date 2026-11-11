package tech.zakomi.zakdroid.editor.extensions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.di.IoDispatcher
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledExtension(
    val manifest: ExtensionManifest,
    /** Shipped inside the app: can be disabled but not removed. */
    val builtIn: Boolean,
    val enabled: Boolean,
)

/**
 * Declarative extensions: built-in ones from the app's assets and user-installed ones in
 * `files/extensions/<id>`. Only data files are read (snippets today); no extension code runs.
 */
@Singleton
class ExtensionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val root = File(context.filesDir, "extensions")
    private val disabledFile = File(root, "disabled.txt")
    private val mutex = Mutex()

    private val _extensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val extensions: StateFlow<List<InstalledExtension>> = _extensions.asStateFlow()

    private var snippets: Map<String, List<Snippet>> = emptyMap()
    private var loaded = false

    /** Snippets of all enabled extensions for a language id (`c`, `cpp`, `python`). */
    suspend fun snippetsFor(language: String): List<Snippet> {
        ensureLoaded()
        return snippets[language].orEmpty()
    }

    suspend fun ensureLoaded() {
        if (!loaded) reload()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        withContext(io) {
            val disabled = readDisabled().toMutableSet()
            if (enabled) disabled -= id else disabled += id
            root.mkdirs()
            disabledFile.writeText(disabled.sorted().joinToString("\n"))
        }
        reload()
    }

    /** Removes a user-installed extension. Built-in ones are ignored. */
    suspend fun uninstall(id: String) {
        withContext(io) {
            val dir = File(root, id)
            if (ExtensionInstaller.isSafeId(id) && dir.isDirectory) dir.deleteRecursively()
        }
        reload()
    }

    /**
     * Installs (or updates) an extension from a .zip. Returns its manifest.
     * Throws [IllegalArgumentException] with a readable reason if the archive is not a valid extension.
     */
    suspend fun install(zip: InputStream): ExtensionManifest {
        val manifest = withContext(io) { ExtensionInstaller.install(zip, root) }
        reload()
        return manifest
    }

    private suspend fun reload() = mutex.withLock {
        val (list, map) = withContext(io) {
            val disabled = readDisabled()
            val found = LinkedHashMap<String, Pair<InstalledExtension, (String) -> String?>>()
            for (name in context.assets.list(ASSET_DIR).orEmpty().sorted()) {
                val read = { path: String -> runCatching { context.assets.open("$ASSET_DIR/$name/$path").use { it.readBytes().decodeToString() } }.getOrNull() }
                val manifest = read(ExtensionManifest.FILE_NAME)?.let { runCatching { ExtensionManifest.parse(it) }.getOrNull() } ?: continue
                found[manifest.id] = InstalledExtension(manifest, builtIn = true, enabled = manifest.id !in disabled) to read
            }
            for (dir in root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name }) {
                val read = { path: String -> runCatching { File(dir, path).readText() }.getOrNull() }
                val manifest = read(ExtensionManifest.FILE_NAME)?.let { runCatching { ExtensionManifest.parse(it) }.getOrNull() } ?: continue
                // A user-installed extension never replaces a built-in one with the same id.
                if (manifest.id != dir.name || found[manifest.id]?.first?.builtIn == true) continue
                found[manifest.id] = InstalledExtension(manifest, builtIn = false, enabled = manifest.id !in disabled) to read
            }
            val byLanguage = HashMap<String, MutableList<Snippet>>()
            for ((extension, read) in found.values) {
                if (!extension.enabled) continue
                for (contribution in extension.manifest.snippets) {
                    val parsed = read(contribution.path)?.let { runCatching { SnippetParser.parse(it) }.getOrNull() }.orEmpty()
                    byLanguage.getOrPut(contribution.language) { ArrayList() } += parsed
                }
            }
            found.values.map { it.first } to byLanguage
        }
        snippets = map
        _extensions.value = list
        loaded = true
    }

    private fun readDisabled(): Set<String> =
        runCatching { disabledFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() }.getOrDefault(emptySet())

    private companion object {
        const val ASSET_DIR = "extensions"
    }
}
