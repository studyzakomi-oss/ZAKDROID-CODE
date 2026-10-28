package tech.zakomi.zakdroid.core.designsystem

import androidx.compose.ui.graphics.Color

/** zakomi TECH brand palette. Every value here is fixed by the brand guide. */
object BrandColors {
    val Navy = Color(0xFF0A1F52)
    val SurfaceDark = Color(0xFF0D1117)
    val SurfaceLight = Color(0xFFFFFFFF)
    val TextOnLight = Color(0xFF000000)
    val Accent = Color(0xFF1E3A8A)
    val TextOnNavy = Color(0xFFFFFFFF)

    /**
     * Accent for text and icons on dark surfaces. [Accent] reaches only ~1.8:1 on
     * [SurfaceDark], so on the dark theme [Accent] is used as a background only.
     */
    val AccentOnDark = Color(0xFF7DA2FF)
}
