package tech.zakomi.zakdroid.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.view.TerminalView

/**
 * Sticky modifier keys of the touch key row. A tap arms CTRL/ALT for the next
 * key only; the view client consumes them when it reads them.
 */
class ExtraKeysState {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)

    /** The view keys are sent to; set by [TerminalPane]. */
    internal var view: TerminalView? = null

    fun consumeCtrl(): Boolean = ctrl.also { if (it) ctrl = false }
    fun consumeAlt(): Boolean = alt.also { if (it) alt = false }
}
