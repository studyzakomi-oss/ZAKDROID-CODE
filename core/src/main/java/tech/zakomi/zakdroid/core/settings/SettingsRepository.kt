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
            prefs[FOLLOW_SYSTEM_THEME] = next.followSystemTheme
            prefs[SIDE_POSITION] = next.sidePosition.storageKey
            prefs[SHOW_STATUS_BAR] = next.showStatusBar
            prefs[UI_SCALE] = next.uiScalePercent.takeIf { it in AppSettings.UI_SCALES } ?: 100
            prefs[LINE_HEIGHT] = next.editorLineHeightPercent.takeIf { it in AppSettings.LINE_HEIGHTS } ?: AppSettings.DEFAULT_LINE_HEIGHT
            prefs[TERMINAL_EXTRA_KEYS] = next.terminalExtraKeys
            prefs[EDITOR_FONT_SIZE] = next.editorFontSizeSp.coerceIn(AppSettings.FONT_SIZE_RANGE)
            prefs[LIGATURES] = next.ligatures
            prefs[WORD_WRAP] = next.wordWrap
            prefs[TAB_SIZE] = next.tabSize
            prefs[LINE_NUMBERS] = next.lineNumbers
            prefs[SHOW_WHITESPACE] = next.showWhitespace
            prefs[STICKY_SCROLL] = next.stickyScroll
            prefs[AUTO_SAVE] = next.autoSave.storageKey
            prefs[TRIM_TRAILING_WHITESPACE] = next.trimTrailingWhitespace
            prefs[INSERT_FINAL_NEWLINE] = next.insertFinalNewline
            prefs[TERMINAL_FONT_SIZE] = next.terminalFontSizeSp.coerceIn(AppSettings.FONT_SIZE_RANGE)
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        theme = ThemeId.fromStorageKey(this[THEME]),
        followSystemTheme = this[FOLLOW_SYSTEM_THEME] ?: false,
        sidePosition = SidePosition.fromStorageKey(this[SIDE_POSITION]),
        showStatusBar = this[SHOW_STATUS_BAR] ?: true,
        uiScalePercent = this[UI_SCALE]?.takeIf { it in AppSettings.UI_SCALES } ?: 100,
        editorLineHeightPercent = this[LINE_HEIGHT]?.takeIf { it in AppSettings.LINE_HEIGHTS } ?: AppSettings.DEFAULT_LINE_HEIGHT,
        terminalExtraKeys = this[TERMINAL_EXTRA_KEYS] ?: true,
        editorFontSizeSp = this[EDITOR_FONT_SIZE] ?: AppSettings.DEFAULT_EDITOR_FONT_SIZE,
        ligatures = this[LIGATURES] ?: true,
        wordWrap = this[WORD_WRAP] ?: false,
        tabSize = this[TAB_SIZE] ?: 4,
        lineNumbers = this[LINE_NUMBERS] ?: true,
        showWhitespace = this[SHOW_WHITESPACE] ?: false,
        stickyScroll = this[STICKY_SCROLL] ?: true,
        autoSave = AutoSave.fromStorageKey(this[AUTO_SAVE]),
        trimTrailingWhitespace = this[TRIM_TRAILING_WHITESPACE] ?: false,
        insertFinalNewline = this[INSERT_FINAL_NEWLINE] ?: false,
        terminalFontSizeSp = this[TERMINAL_FONT_SIZE] ?: AppSettings.DEFAULT_TERMINAL_FONT_SIZE,
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val FOLLOW_SYSTEM_THEME = booleanPreferencesKey("workbench.followSystemTheme")
        val SIDE_POSITION = stringPreferencesKey("workbench.sideBar.location")
        val SHOW_STATUS_BAR = booleanPreferencesKey("workbench.statusBar.visible")
        val UI_SCALE = intPreferencesKey("window.zoomPercent")
        val LINE_HEIGHT = intPreferencesKey("editor.lineHeightPercent")
        val TERMINAL_EXTRA_KEYS = booleanPreferencesKey("terminal.extraKeys")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor.fontSize")
        val LIGATURES = booleanPreferencesKey("editor.ligatures")
        val WORD_WRAP = booleanPreferencesKey("editor.wordWrap")
        val TAB_SIZE = intPreferencesKey("editor.tabSize")
        val LINE_NUMBERS = booleanPreferencesKey("editor.lineNumbers")
        val SHOW_WHITESPACE = booleanPreferencesKey("editor.renderWhitespace")
        val STICKY_SCROLL = booleanPreferencesKey("editor.stickyScroll")
        val AUTO_SAVE = stringPreferencesKey("files.autoSave")
        val TRIM_TRAILING_WHITESPACE = booleanPreferencesKey("files.trimTrailingWhitespace")
        val INSERT_FINAL_NEWLINE = booleanPreferencesKey("files.insertFinalNewline")
        val TERMINAL_FONT_SIZE = intPreferencesKey("terminal.fontSize")
    }
}
