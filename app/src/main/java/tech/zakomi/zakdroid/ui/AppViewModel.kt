package tech.zakomi.zakdroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.zakomi.zakdroid.core.designsystem.ZakomiLightColors
import tech.zakomi.zakdroid.core.designsystem.ZakomiNavyDarkColors
import tech.zakomi.zakdroid.core.settings.AppSettings
import tech.zakomi.zakdroid.core.settings.SettingsRepository
import tech.zakomi.zakdroid.core.settings.ThemeId
import tech.zakomi.zakdroid.editor.textmate.TextMateSetup
import tech.zakomi.zakdroid.terminal.TerminalPalette
import tech.zakomi.zakdroid.terminal.TerminalSessionManager
import androidx.compose.ui.graphics.toArgb
import javax.inject.Inject

/** App-wide state: settings, theme propagation to editor and terminal, and startup. */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val textMate: TextMateSetup,
    private val terminals: TerminalSessionManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ready = MutableStateFlow(false)

    /** False while the branded splash is shown (settings, grammars and themes loading). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val first = settingsRepository.settings.first()
            applyTheme(first.theme)
            textMate.ensureInitialized()
            // Keep the brand splash visible long enough to be read, but never block longer than needed.
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < MIN_SPLASH_MS) delay(MIN_SPLASH_MS - elapsed)
            _ready.value = true
        }
        viewModelScope.launch {
            settingsRepository.settings.map { it.theme }.distinctUntilChanged().collect { applyTheme(it) }
        }
    }

    private suspend fun applyTheme(theme: ThemeId) {
        val colors = if (theme == ThemeId.NavyDark) ZakomiNavyDarkColors else ZakomiLightColors
        TerminalPalette.apply(
            dark = colors.isDark,
            foreground = colors.terminalForeground.toArgb(),
            background = colors.terminalBackground.toArgb(),
            cursor = colors.terminalCursor.toArgb(),
        )
        terminals.refreshColors()
        textMate.applyTheme(theme)
    }

    private companion object {
        const val MIN_SPLASH_MS = 900L
    }
}
