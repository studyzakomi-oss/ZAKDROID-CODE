package tech.zakomi.zakdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.zakomi.zakdroid.core.designsystem.ZakdroidTheme
import tech.zakomi.zakdroid.core.settings.ThemeId
import tech.zakomi.zakdroid.ui.about.AboutScreen
import tech.zakomi.zakdroid.ui.extensions.ExtensionsScreen
import tech.zakomi.zakdroid.ui.settings.SettingsScreen
import tech.zakomi.zakdroid.ui.splash.BrandSplash
import tech.zakomi.zakdroid.ui.workbench.WorkbenchScreen

private enum class Screen { Workbench, Settings, About, Extensions }

@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Workbench) }

    val systemIsDark = isSystemInDarkTheme()
    val theme = settings?.effectiveTheme(systemIsDark)
    LaunchedEffect(theme) { theme?.let(viewModel::onThemeShown) }
    // Interface zoom: scaling the density scales every dp and sp of the Compose UI.
    val density = LocalDensity.current
    val scale = (settings?.uiScalePercent ?: 100) / 100f
    val scaled = remember(density, scale) { Density(density.density * scale, density.fontScale) }

    CompositionLocalProvider(LocalDensity provides scaled) {
    ZakdroidTheme(theme ?: ThemeId.NavyDark) {
        val current = settings
        if (!ready || current == null) {
            BrandSplash()
            return@ZakdroidTheme
        }
        BackHandler(enabled = screen != Screen.Workbench) {
            screen = if (screen == Screen.About) Screen.Settings else Screen.Workbench
        }
        AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { target ->
            when (target) {
                Screen.Workbench -> WorkbenchScreen(
                    settings = current,
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenExtensions = { screen = Screen.Extensions },
                    onUpdateSettings = viewModel::updateSettings,
                )
                Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Workbench }, onOpenAbout = { screen = Screen.About })
                Screen.About -> AboutScreen(onBack = { screen = Screen.Settings })
                Screen.Extensions -> ExtensionsScreen(onBack = { screen = Screen.Workbench })
            }
        }
    }
    }
}
