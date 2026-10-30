package tech.zakomi.zakdroid.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.system.BackgroundRestrictions

/**
 * Shows whether Android may stop zakdroid-code in the background and how to prevent it.
 * Re-checked every time the screen resumes (the user returns from system settings).
 * With [onlyWhenRestricted], nothing is shown once the app is exempt.
 */
@Composable
fun BackgroundActivityCard(onlyWhenRestricted: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = WorkbenchTheme
    var exempt by remember { mutableStateOf(BackgroundRestrictions.isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        exempt = BackgroundRestrictions.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    val honor = BackgroundRestrictions.vendor == BackgroundRestrictions.Vendor.HonorOrHuawei
    if (onlyWhenRestricted && exempt) return

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.listHover, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(
            stringResource(if (exempt) R.string.background_ok else R.string.background_restricted),
            color = if (exempt) colors.success else colors.warning,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(R.string.background_why), color = colors.textSecondary, fontSize = 13.sp)
        if (honor) {
            Text(stringResource(R.string.background_honor_steps), color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            if (!exempt) {
                OutlinedButton(onClick = { BackgroundRestrictions.requestIgnoreBatteryOptimizations(context) }) {
                    Text(stringResource(R.string.background_allow))
                }
            }
            if (honor) {
                OutlinedButton(onClick = { BackgroundRestrictions.openVendorLaunchSettings(context) }) {
                    Text(stringResource(R.string.background_open_app_launch))
                }
            }
        }
    }
}
