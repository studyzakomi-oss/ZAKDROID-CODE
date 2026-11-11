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

/** Which side the Activity Bar and Side Bar are on. [storageKey] is persisted, so it must never change. */
enum class SidePosition(val storageKey: String) {
    Start("start"),
    End("end");

    companion object {
        fun fromStorageKey(key: String?): SidePosition = entries.firstOrNull { it.storageKey == key } ?: Start
    }
}

data class AppSettings(
    val theme: ThemeId = ThemeId.NavyDark,
    /** Use the light or dark theme that matches the device setting, instead of [theme]. */
    val followSystemTheme: Boolean = false,
    val sidePosition: SidePosition = SidePosition.Start,
    val showStatusBar: Boolean = true,
    /** Zoom of the whole interface in percent (the editor and terminal have their own font sizes). */
    val uiScalePercent: Int = 100,
    val editorLineHeightPercent: Int = DEFAULT_LINE_HEIGHT,
    val terminalExtraKeys: Boolean = true,
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
    /** The theme to show: [theme], or the one matching the device when [followSystemTheme] is on. */
    fun effectiveTheme(systemIsDark: Boolean): ThemeId =
        if (followSystemTheme) (if (systemIsDark) ThemeId.NavyDark else ThemeId.Light) else theme

    companion object {
        const val DEFAULT_EDITOR_FONT_SIZE = 14
        const val DEFAULT_TERMINAL_FONT_SIZE = 13
        val FONT_SIZE_RANGE = 8..32
        val TAB_SIZES = listOf(2, 4, 8)
        val UI_SCALES = listOf(85, 100, 115, 130)
        const val DEFAULT_LINE_HEIGHT = 100
        val LINE_HEIGHTS = listOf(100, 120, 140, 160)

        /** Quiet time after the last edit before an auto save. */
        const val AUTO_SAVE_DELAY_MS = 1_000L
    }
}
