package tech.zakomi.zakdroid.editor.textmate

import android.graphics.Color
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry

/**
 * TextMate color scheme plus the editor chrome that VS Code themes do not cover
 * (line-number panel, handles, scrollbars, sticky-scroll divider).
 * Re-applied automatically whenever [ThemeRegistry] switches theme.
 */
class ZakdroidColorScheme(registry: ThemeRegistry) :
    TextMateColorScheme(registry, registry.currentThemeModel) {

    override fun applyDefault() {
        super.applyDefault()
        val dark = isDark
        val accent = if (dark) ACCENT_ON_DARK else ACCENT
        val background = getColor(WHOLE_BACKGROUND)
        setColor(LINE_NUMBER_BACKGROUND, background)
        setColor(LINE_NUMBER_PANEL, if (dark) 0xFF0F1626.toInt() else 0xFFF3F5FA.toInt())
        setColor(LINE_NUMBER_PANEL_TEXT, if (dark) 0xFFE6EDF3.toInt() else Color.BLACK)
        setColor(LINE_DIVIDER, Color.TRANSPARENT)
        setColor(SELECTION_HANDLE, accent)
        setColor(SELECTION_INSERT, accent)
        setColor(SCROLL_BAR_THUMB, if (dark) 0x667D8CA8 else 0x66576480)
        setColor(SCROLL_BAR_THUMB_PRESSED, accent)
        setColor(SCROLL_BAR_TRACK, Color.TRANSPARENT)
        setColor(HIGHLIGHTED_DELIMITERS_UNDERLINE, accent)
        setColor(STICKY_SCROLL_DIVIDER, if (dark) 0xFF243048.toInt() else 0xFFD0D7E2.toInt())
        setColor(TEXT_ACTION_WINDOW_BACKGROUND, if (dark) 0xFF0F1626.toInt() else Color.WHITE)
        setColor(TEXT_ACTION_WINDOW_ICON_COLOR, if (dark) 0xFFE6EDF3.toInt() else Color.BLACK)
        setColor(COMPLETION_WND_CORNER, if (dark) 0xFF243048.toInt() else 0xFFD0D7E2.toInt())
    }

    private companion object {
        const val ACCENT = 0xFF1E3A8A.toInt()
        const val ACCENT_ON_DARK = 0xFF7DA2FF.toInt()
    }
}
