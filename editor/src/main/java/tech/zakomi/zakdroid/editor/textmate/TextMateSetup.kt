package tech.zakomi.zakdroid.editor.textmate

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import tech.zakomi.zakdroid.core.settings.ThemeId
import tech.zakomi.zakdroid.editor.EditorLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the process-wide TextMate registries: VS Code grammars for C, C++ and
 * Python, plus the two zakomi themes. Grammar parsing is slow (the C++ grammar
 * is ~1 MB of JSON), so everything here runs off the main thread.
 */
@Singleton
class TextMateSetup @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    @Volatile private var initialized = false

    suspend fun ensureInitialized() = mutex.withLock {
        if (initialized) return@withLock
        withContext(Dispatchers.Default) {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
            val themes = ThemeRegistry.getInstance()
            ThemeId.entries.forEach { id ->
                val path = "textmate/themes/${id.textMateThemeName}.json"
                val source = IThemeSource.fromInputStream(FileProviderRegistry.getInstance().tryGetInputStream(path), path, null)
                themes.loadTheme(ThemeModel(source, id.textMateThemeName).apply { isDark = id == ThemeId.NavyDark })
            }
            themes.setTheme(ThemeId.NavyDark.textMateThemeName)
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        }
        initialized = true
    }

    /** Switches the active editor theme; every TextMate color scheme follows it. */
    suspend fun applyTheme(theme: ThemeId) {
        ensureInitialized()
        withContext(Dispatchers.Main) { ThemeRegistry.getInstance().setTheme(theme.textMateThemeName) }
    }

    /** Parses the grammar for [language] in the background so [newLanguage] is cheap. */
    suspend fun preload(language: EditorLanguage) {
        val scope = language.scopeName ?: return
        ensureInitialized()
        withContext(Dispatchers.Default) {
            synchronized(registryLock) { GrammarRegistry.getInstance().findGrammar(scope) }
        }
    }

    /**
     * Creates a fresh language instance. The editor destroys the previous instance
     * whenever the language changes, so one is created per tab switch.
     * Call [preload] first; the grammar is then already cached.
     */
    fun newLanguage(language: EditorLanguage): Language {
        val scope = language.scopeName
        if (scope == null || !initialized) return EmptyLanguage()
        return synchronized(registryLock) { TextMateLanguage.create(scope, true) }
    }

    private val registryLock = Any()
}
