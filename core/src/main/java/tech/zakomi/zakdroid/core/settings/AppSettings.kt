package tech.zakomi.zakdroid.core.settings

/** Built-in themes. [storageKey] is persisted, so it must never change. */
enum class ThemeId(val storageKey: String, val textMateThemeName: String) {
    NavyDark("navy-dark", "zakomi-navy-dark"),
    Light("light", "zakomi-light");

    companion object {
        fun fromStorageKey(key: String?): ThemeId = entries.firstOrNull { it.storageKey == key } ?: NavyDark
    }
}

data class AppSettings(
    val theme: ThemeId = ThemeId.NavyDark,
    val editorFontSizeSp: Int = DEFAULT_EDITOR_FONT_SIZE,
    val ligatures: Boolean = true,
    val wordWrap: Boolean = false,
    val tabSize: Int = 4,
    val terminalFontSizeSp: Int = DEFAULT_TERMINAL_FONT_SIZE,
) {
    companion object {
        const val DEFAULT_EDITOR_FONT_SIZE = 14
        const val DEFAULT_TERMINAL_FONT_SIZE = 13
        val FONT_SIZE_RANGE = 8..32
        val TAB_SIZES = listOf(2, 4, 8)
    }
}
