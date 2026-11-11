package tech.zakomi.zakdroid.ui.palette

/**
 * An entry of the Command Palette. [shortcut] is the hardware-keyboard binding shown
 * next to the title (VS Code's default keymap where one exists).
 */
class WorkbenchCommand(
    val title: String,
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val run: () -> Unit,
)
