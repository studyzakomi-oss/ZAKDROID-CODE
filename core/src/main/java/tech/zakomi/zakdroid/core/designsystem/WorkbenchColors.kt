package tech.zakomi.zakdroid.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors of the VS Code-style workbench. Contrast of every text/background pair
 * is checked by `scripts/check_contrast.py` (WCAG AA: 4.5:1 text, 3:1 UI parts).
 */
@Immutable
data class WorkbenchColors(
    val isDark: Boolean,
    val activityBar: Color,
    val activityBarIcon: Color,
    val activityBarIconActive: Color,
    val activityBarIndicator: Color,
    /** Background of the selected Activity Bar item. */
    val activityBarActive: Color,
    val sideBar: Color,
    val sideBarHeader: Color,
    val editor: Color,
    val tabBar: Color,
    val tabActive: Color,
    val tabInactive: Color,
    val tabActiveText: Color,
    val tabInactiveText: Color,
    val tabActiveIndicator: Color,
    val panel: Color,
    val statusBar: Color,
    val statusBarText: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentText: Color,
    val selection: Color,
    val selectionText: Color,
    val listHover: Color,
    /** Dialogs, menus and other surfaces that float above the workbench. */
    val surfaceRaised: Color,
    val error: Color,
    val warning: Color,
    val success: Color,
    val terminalBackground: Color,
    val terminalForeground: Color,
    val terminalCursor: Color,
)

/**
 * The dark palette follows the reference design (docs/NEXT_STEPS.md, section 4.2): every area is
 * a slightly different navy, the terminal is the darkest, and blue is used for what is active.
 */
val ZakomiNavyDarkColors = WorkbenchColors(
    isDark = true,
    activityBar = Color(0xFF0F1526),
    activityBarIcon = Color(0xFFA9B8DE),
    activityBarIconActive = BrandColors.TextOnNavy,
    activityBarIndicator = Color(0xFF2B75EF),
    activityBarActive = Color(0xFF0F2554),
    sideBar = Color(0xFF0F1628),
    sideBarHeader = Color(0xFF0F1628),
    editor = Color(0xFF101524),
    tabBar = Color(0xFF0F1526),
    tabActive = Color(0xFF121E38),
    tabInactive = Color(0xFF0F1526),
    tabActiveText = Color(0xFFE6EDF3),
    tabInactiveText = Color(0xFF9AA5B8),
    tabActiveIndicator = Color(0xFF2B75EF),
    panel = Color(0xFF0A101F),
    statusBar = Color(0xFF0F1526),
    statusBarText = Color(0xFFE6EDF3),
    border = Color(0xFF243048),
    textPrimary = Color(0xFFE6EDF3),
    textSecondary = Color(0xFF9AA5B8),
    accentText = BrandColors.AccentOnDark,
    selection = Color(0xFF15295A),
    selectionText = Color(0xFFFFFFFF),
    listHover = Color(0xFF161E32),
    surfaceRaised = Color(0xFF161E32),
    error = Color(0xFFFF7B72),
    warning = Color(0xFFE3B341),
    success = Color(0xFF4DD180),
    terminalBackground = Color(0xFF0A101F),
    terminalForeground = Color(0xFFE6EDF3),
    terminalCursor = BrandColors.AccentOnDark,
)

val ZakomiLightColors = WorkbenchColors(
    isDark = false,
    activityBar = BrandColors.Navy,
    activityBarIcon = Color(0xFFA9B8DE),
    activityBarIconActive = BrandColors.TextOnNavy,
    activityBarIndicator = BrandColors.TextOnNavy,
    activityBarActive = BrandColors.Accent,
    sideBar = Color(0xFFF3F5FA),
    sideBarHeader = Color(0xFFF3F5FA),
    editor = BrandColors.SurfaceLight,
    tabBar = Color(0xFFE9EDF5),
    tabActive = BrandColors.SurfaceLight,
    tabInactive = Color(0xFFE9EDF5),
    tabActiveText = BrandColors.TextOnLight,
    tabInactiveText = Color(0xFF4A5568),
    tabActiveIndicator = BrandColors.Accent,
    panel = BrandColors.SurfaceLight,
    statusBar = BrandColors.Navy,
    statusBarText = BrandColors.TextOnNavy,
    border = Color(0xFFD0D7E2),
    textPrimary = BrandColors.TextOnLight,
    textSecondary = Color(0xFF4A5568),
    accentText = BrandColors.Accent,
    selection = Color(0xFFC9D6F5),
    selectionText = BrandColors.TextOnLight,
    listHover = Color(0xFFE3E9F4),
    surfaceRaised = BrandColors.SurfaceLight,
    error = Color(0xFFB42318),
    warning = Color(0xFF8A5A00),
    success = Color(0xFF1A7F37),
    terminalBackground = BrandColors.SurfaceLight,
    terminalForeground = BrandColors.TextOnLight,
    terminalCursor = BrandColors.Accent,
)

val LocalWorkbenchColors = staticCompositionLocalOf { ZakomiNavyDarkColors }
