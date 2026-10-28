package tech.zakomi.zakdroid.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.BrandCapsStyle
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.designsystem.ZakomiLogo

/** Branded splash: logo, "zakdroid-code", and "by zakomi TECH" in spaced small caps. */
@Composable
fun BrandSplash() {
    val colors = WorkbenchTheme
    Column(
        modifier = Modifier.fillMaxSize().background(colors.editor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ZakomiLogo(size = 112.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.brand_by).uppercase(),
            style = BrandCapsStyle,
            color = colors.accentText,
        )
    }
}
