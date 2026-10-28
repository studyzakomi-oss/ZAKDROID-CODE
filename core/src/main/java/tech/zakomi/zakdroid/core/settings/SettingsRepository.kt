package tech.zakomi.zakdroid.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[THEME] = next.theme.storageKey
            prefs[EDITOR_FONT_SIZE] = next.editorFontSizeSp.coerceIn(AppSettings.FONT_SIZE_RANGE)
            prefs[LIGATURES] = next.ligatures
            prefs[WORD_WRAP] = next.wordWrap
            prefs[TAB_SIZE] = next.tabSize
            prefs[TERMINAL_FONT_SIZE] = next.terminalFontSizeSp.coerceIn(AppSettings.FONT_SIZE_RANGE)
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        theme = ThemeId.fromStorageKey(this[THEME]),
        editorFontSizeSp = this[EDITOR_FONT_SIZE] ?: AppSettings.DEFAULT_EDITOR_FONT_SIZE,
        ligatures = this[LIGATURES] ?: true,
        wordWrap = this[WORD_WRAP] ?: false,
        tabSize = this[TAB_SIZE] ?: 4,
        terminalFontSizeSp = this[TERMINAL_FONT_SIZE] ?: AppSettings.DEFAULT_TERMINAL_FONT_SIZE,
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor.fontSize")
        val LIGATURES = booleanPreferencesKey("editor.ligatures")
        val WORD_WRAP = booleanPreferencesKey("editor.wordWrap")
        val TAB_SIZE = intPreferencesKey("editor.tabSize")
        val TERMINAL_FONT_SIZE = intPreferencesKey("terminal.fontSize")
    }
}
