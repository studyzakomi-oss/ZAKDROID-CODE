package tech.zakomi.zakdroid.terminal

import com.termux.terminal.TerminalColors
import java.util.Properties

/** ANSI palettes tuned for each theme so that all 16 colors stay readable on its background. */
object TerminalPalette {
    private val DARK = listOf(
        "#484F58", "#FF7B72", "#3FB950", "#D29922", "#7DA2FF", "#BC8CFF", "#39C5CF", "#B1BAC4",
        "#6E7681", "#FFA198", "#56D364", "#E3B341", "#A9C1FF", "#D2A8FF", "#56D4DD", "#FFFFFF",
    )
    private val LIGHT = listOf(
        "#24292F", "#B42318", "#1A7F37", "#8A5A00", "#1E3A8A", "#8250DF", "#1B7C83", "#57606A",
        "#4A5568", "#A40E26", "#116329", "#7D4E00", "#0550AE", "#6639BA", "#0B6E4F", "#32383F",
    )

    /** Sets the default colors used by every terminal emulator created from now on. */
    fun apply(dark: Boolean, foreground: Int, background: Int, cursor: Int) {
        val props = Properties()
        (if (dark) DARK else LIGHT).forEachIndexed { i, c -> props.setProperty("color$i", c) }
        props.setProperty("foreground", hex(foreground))
        props.setProperty("background", hex(background))
        props.setProperty("cursor", hex(cursor))
        TerminalColors.COLOR_SCHEME.updateWith(props)
    }

    private fun hex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)
}
