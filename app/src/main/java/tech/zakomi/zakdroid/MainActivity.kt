package tech.zakomi.zakdroid

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import tech.zakomi.zakdroid.ui.AppRoot

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Hardware-keyboard shortcuts, installed by the workbench while it is shown. */
    var shortcutHandler: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.callback = ShortcutWindowCallback(window.callback) { event -> shortcutHandler?.invoke(event) == true }
        setContent { AppRoot() }
    }
}
