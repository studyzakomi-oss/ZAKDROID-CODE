package tech.zakomi.zakdroid.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import tech.zakomi.zakdroid.core.R

/** UI typeface. Glyphs it lacks (e.g. Arabic) fall back to the system font. */
val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

/** Code typeface, used by the editor and the terminal. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Spaced small-caps label, as in "T E C H" under the logo. */
val BrandCapsStyle = TextStyle(
    fontFamily = Montserrat,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    letterSpacing = 0.35.em,
)

val ZakdroidTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Montserrat, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = Montserrat, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = Montserrat, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = Montserrat, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Montserrat, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Montserrat, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Montserrat, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = Montserrat),
        bodyMedium = base.bodyMedium.copy(fontFamily = Montserrat),
        bodySmall = base.bodySmall.copy(fontFamily = Montserrat),
        labelLarge = base.labelLarge.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.em),
    )
}
