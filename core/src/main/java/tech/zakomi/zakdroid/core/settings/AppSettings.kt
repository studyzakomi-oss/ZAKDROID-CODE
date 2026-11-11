package tech.zakomi.zakdroid.core.settings

/** Built-in themes. [storageKey] is persisted, so it must never change. */
enum class ThemeId(val storageKey: String, val textMateThemeName: String) {
    NavyDark("navy-dark", "zakomi-navy-dark"),
    Light("light", "zakomi-light");

    companion object {
        fun fromStorageKey(key: String?): ThemeId = entries.firstOrNull { it.storageKey == key } ?: NavyDark
    }
}

/** When edited files are written to disk on their own. [storageKey] is persisted, so it must never change. */
enum class AutoSave(val storageKey: String) {
    Off("off"),
    AfterDelay("afterDelay");

    companion object {
        fun fromStorageKey(key: String?): AutoSave = entries.firstOrNull { it.storageKey == key } ?: Off
    }
}

data class AppSettings(
    val theme: ThemeId = ThemeId.NavyDark,
    val editorFontSizeSp: Int = DEFAULT_EDITOR_FONT_SIZE,
    val ligatures: Boolean = true,
    val wordWrap: Boolean = false,
    val tabSize: Int = 4,
    val lineNumbers: Boolean = true,
    val showWhitespace: Boolean = false,
    val stickyScroll: Boolean = true,
    val autoSave: AutoSave = AutoSave.Off,
    val trimTrailingWhitespace: Boolean = false,
    val insertFinalNewline: Boolean = false,
    val terminalFontSizeSp: Int = DEFAULT_TERMINAL_FONT_SIZE,
) {
    companion object {
        const val DEFAULT_EDITOR_FONT_SIZE = 14
        const val DEFAULT_TERMINAL_FONT_SIZE = 13
        val FONT_SIZE_RANGE = 8..32
        val TAB_SIZES = listOf(2, 4, 8)

        /** Quiet time after the last edit before an auto save. */
        const val AUTO_SAVE_DELAY_MS = 1_000L
    }
}
