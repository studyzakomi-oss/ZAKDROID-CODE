package tech.zakomi.zakdroid.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import tech.zakomi.zakdroid.core.settings.ThemeId

private val NavyDarkScheme = darkColorScheme(
    primary = BrandColors.AccentOnDark,
    onPrimary = BrandColors.Navy,
    primaryContainer = BrandColors.Accent,
    onPrimaryContainer = BrandColors.TextOnNavy,
    secondary = ZakomiNavyDarkColors.textSecondary,
    onSecondary = BrandColors.SurfaceDark,
    background = BrandColors.SurfaceDark,
    onBackground = ZakomiNavyDarkColors.textPrimary,
    surface = ZakomiNavyDarkColors.sideBar,
    onSurface = ZakomiNavyDarkColors.textPrimary,
    surfaceVariant = ZakomiNavyDarkColors.listHover,
    onSurfaceVariant = ZakomiNavyDarkColors.textSecondary,
    surfaceContainer = ZakomiNavyDarkColors.sideBar,
    surfaceContainerHigh = ZakomiNavyDarkColors.listHover,
    surfaceContainerHighest = ZakomiNavyDarkColors.listHover,
    outline = ZakomiNavyDarkColors.border,
    outlineVariant = ZakomiNavyDarkColors.border,
    error = ZakomiNavyDarkColors.error,
    onError = BrandColors.SurfaceDark,
)

private val LightScheme = lightColorScheme(
    primary = BrandColors.Accent,
    onPrimary = BrandColors.TextOnNavy,
    primaryContainer = ZakomiLightColors.selection,
    onPrimaryContainer = BrandColors.TextOnLight,
    secondary = ZakomiLightColors.textSecondary,
    onSecondary = BrandColors.SurfaceLight,
    background = BrandColors.SurfaceLight,
    onBackground = BrandColors.TextOnLight,
    surface = BrandColors.SurfaceLight,
    onSurface = BrandColors.TextOnLight,
    surfaceVariant = ZakomiLightColors.sideBar,
    onSurfaceVariant = ZakomiLightColors.textSecondary,
    surfaceContainer = ZakomiLightColors.sideBar,
    surfaceContainerHigh = ZakomiLightColors.listHover,
    surfaceContainerHighest = ZakomiLightColors.listHover,
    outline = ZakomiLightColors.border,
    outlineVariant = ZakomiLightColors.border,
    error = ZakomiLightColors.error,
    onError = BrandColors.TextOnNavy,
)

@Composable
fun ZakdroidTheme(themeId: ThemeId, content: @Composable () -> Unit) {
    val workbench = when (themeId) {
        ThemeId.NavyDark -> ZakomiNavyDarkColors
        ThemeId.Light -> ZakomiLightColors
    }
    CompositionLocalProvider(LocalWorkbenchColors provides workbench) {
        MaterialTheme(
            colorScheme = if (workbench.isDark) NavyDarkScheme else LightScheme,
            typography = ZakdroidTypography,
            content = content,
        )
    }
}

/** Shorthand for the current workbench palette. */
val WorkbenchTheme: WorkbenchColors
    @Composable get() = LocalWorkbenchColors.current
