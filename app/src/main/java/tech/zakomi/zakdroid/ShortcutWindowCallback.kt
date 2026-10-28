package tech.zakomi.zakdroid

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.Window

/**
 * Sees key events before the view hierarchy, so workbench shortcuts (Ctrl+S, F5, …)
 * work while the code editor or terminal has focus. Everything else goes to [base].
 */
class ShortcutWindowCallback(
    private val base: Window.Callback,
    private val onShortcut: (KeyEvent) -> Boolean,
) : Window.Callback by base {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && onShortcut(event)) return true
        return base.dispatchKeyEvent(event)
    }

    // Java default methods are not covered by Kotlin delegation; forward them explicitly.
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int) =
        base.onProvideKeyboardShortcuts(data, menu, deviceId)

    override fun onPointerCaptureChanged(hasCapture: Boolean) = base.onPointerCaptureChanged(hasCapture)
}
