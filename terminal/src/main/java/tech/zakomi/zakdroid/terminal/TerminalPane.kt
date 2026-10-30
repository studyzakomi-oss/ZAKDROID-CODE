package tech.zakomi.zakdroid.terminal

import android.util.TypedValue
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.termux.view.TerminalView
import tech.zakomi.zakdroid.core.R as CoreR

/**
 * Shows the session [sessionId] of [manager]. Always left-to-right, also in Arabic UI.
 *
 * @param onFontScale called with a pinch scale factor; the caller adjusts the font size setting.
 */
@Composable
fun TerminalPane(
    manager: TerminalSessionManager,
    sessionId: Int,
    fontSizeSp: Int,
    keys: ExtraKeysState,
    onFontScale: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnFontScale = rememberUpdatedState(onFontScale)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TerminalView(context, null).apply {
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    isFocusable = true
                    isFocusableInTouchMode = true
                    val self = this
                    setTerminalViewClient(
                        ZdTerminalViewClient(
                            view = { self },
                            keys = keys,
                            onEmulatorReady = { self.invalidate() },
                            onFontScale = { currentOnFontScale.value(it) },
                        ),
                    )
                    // Order matters: TerminalView creates its renderer in setTextSize(); calling
                    // setTypeface() first dereferences a null renderer and crashes the app.
                    val px = textSizePx(fontSizeSp, resources.displayMetrics)
                    setTextSize(px)
                    tag = px
                    ResourcesCompat.getFont(context, CoreR.font.jetbrains_mono_regular)?.let(::setTypeface)
                    manager.attachedView = this
                    keys.view = this
                }
            },
            update = { view ->
                val px = textSizePx(fontSizeSp, view.resources.displayMetrics)
                // The view keeps no public text size; remember the applied one in its tag.
                if (view.tag != px) {
                    view.setTextSize(px)
                    view.tag = px
                }
                val session = manager.session(sessionId)
                if (session != null && view.currentSession !== session) {
                    view.attachSession(session)
                    view.requestFocus()
                }
                manager.attachedView = view
                keys.view = view
            },
            onRelease = { view ->
                if (manager.attachedView === view) manager.attachedView = null
                if (keys.view === view) keys.view = null
            },
        )
    }
}

private fun textSizePx(sp: Int, metrics: android.util.DisplayMetrics): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), metrics).toInt()
