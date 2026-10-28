package tech.zakomi.zakdroid.terminal

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.terminal.KeyHandler
import tech.zakomi.zakdroid.core.designsystem.JetBrainsMono
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme

private sealed interface ExtraKey {
    val label: String
    data class Code(override val label: String, val keyCode: Int, val description: Int) : ExtraKey
    data class Char(override val label: String) : ExtraKey
    data object Ctrl : ExtraKey { override val label = "CTRL" }
    data object Alt : ExtraKey { override val label = "ALT" }
}

private val KEYS: List<ExtraKey> = listOf(
    ExtraKey.Code("ESC", KeyEvent.KEYCODE_ESCAPE, R.string.terminal_key_esc),
    ExtraKey.Code("TAB", KeyEvent.KEYCODE_TAB, R.string.terminal_key_tab),
    ExtraKey.Ctrl,
    ExtraKey.Alt,
    ExtraKey.Code("←", KeyEvent.KEYCODE_DPAD_LEFT, R.string.terminal_key_left),
    ExtraKey.Code("↓", KeyEvent.KEYCODE_DPAD_DOWN, R.string.terminal_key_down),
    ExtraKey.Code("↑", KeyEvent.KEYCODE_DPAD_UP, R.string.terminal_key_up),
    ExtraKey.Code("→", KeyEvent.KEYCODE_DPAD_RIGHT, R.string.terminal_key_right),
    ExtraKey.Code("HOME", KeyEvent.KEYCODE_MOVE_HOME, R.string.terminal_key_home),
    ExtraKey.Code("END", KeyEvent.KEYCODE_MOVE_END, R.string.terminal_key_end),
    ExtraKey.Code("PGUP", KeyEvent.KEYCODE_PAGE_UP, R.string.terminal_key_pgup),
    ExtraKey.Code("PGDN", KeyEvent.KEYCODE_PAGE_DOWN, R.string.terminal_key_pgdn),
    ExtraKey.Char("-"),
    ExtraKey.Char("/"),
    ExtraKey.Char("|"),
    ExtraKey.Char("~"),
    ExtraKey.Char("_"),
    ExtraKey.Char("&"),
)

/** Keys that soft keyboards lack, scrollable on narrow phones. Keeps LTR order in Arabic UI. */
@Composable
fun ExtraKeysRow(keys: ExtraKeysState, modifier: Modifier = Modifier) {
    val colors = WorkbenchTheme
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier
                .fillMaxWidth()
                .background(colors.tabBar)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            KEYS.forEach { key ->
                val active = (key is ExtraKey.Ctrl && keys.ctrl) || (key is ExtraKey.Alt && keys.alt)
                val description = when (key) {
                    is ExtraKey.Code -> stringResource(key.description)
                    is ExtraKey.Ctrl -> stringResource(R.string.terminal_key_ctrl)
                    is ExtraKey.Alt -> stringResource(R.string.terminal_key_alt)
                    is ExtraKey.Char -> key.label
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(40.dp)
                        .widthIn(min = 44.dp)
                        .background(if (active) colors.selection else colors.sideBar, RoundedCornerShape(6.dp))
                        .clickable { press(key, keys) }
                        .semantics {
                            contentDescription = description
                            if (key is ExtraKey.Ctrl || key is ExtraKey.Alt) {
                                role = Role.Switch
                                selected = active
                            } else {
                                role = Role.Button
                            }
                        }
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        key.label,
                        color = if (active) colors.selectionText else colors.textPrimary,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun press(key: ExtraKey, keys: ExtraKeysState) {
    when (key) {
        ExtraKey.Ctrl -> keys.ctrl = !keys.ctrl
        ExtraKey.Alt -> keys.alt = !keys.alt
        is ExtraKey.Code -> {
            val view = keys.view ?: return
            if (view.currentSession == null) return
            var mod = 0
            if (keys.consumeCtrl()) mod = mod or KeyHandler.KEYMOD_CTRL
            if (keys.consumeAlt()) mod = mod or KeyHandler.KEYMOD_ALT
            view.handleKeyCode(key.keyCode, mod)
        }
        is ExtraKey.Char -> {
            val view = keys.view ?: return
            if (view.currentSession == null) return
            view.inputCodePoint(key.label.codePointAt(0), keys.consumeCtrl(), keys.consumeAlt())
        }
    }
}
