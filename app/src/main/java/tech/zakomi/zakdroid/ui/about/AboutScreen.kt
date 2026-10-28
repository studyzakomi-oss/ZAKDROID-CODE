package tech.zakomi.zakdroid.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.zakomi.zakdroid.BuildConfig
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.BrandCapsStyle
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.designsystem.ZakomiLogo
import tech.zakomi.zakdroid.ui.settings.ScreenScaffold

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = WorkbenchTheme
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().background(colors.editor).padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ZakomiLogo(size = 96.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = colors.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.brand_by).uppercase(), style = BrandCapsStyle, color = colors.accentText)
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.about_license), color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_trademark), color = colors.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.about_third_party),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    TableRow(
                        stringResource(R.string.about_col_component),
                        stringResource(R.string.about_col_license),
                        stringResource(R.string.about_col_purpose),
                        header = true,
                    )
                }
            }
            items(ThirdPartyComponents) { component ->
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                    HorizontalDivider(color = colors.border)
                    TableRow(component.name, component.license, stringResource(component.purpose), header = false)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun TableRow(component: String, license: String, purpose: String, header: Boolean) {
    val colors = WorkbenchTheme
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    val color = if (header) colors.textPrimary else colors.textSecondary
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(component, Modifier.weight(0.42f).padding(end = 8.dp), color = colors.textPrimary, fontWeight = if (header) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp)
        Text(license, Modifier.weight(0.25f).padding(end = 8.dp), color = if (header) color else colors.accentText, fontWeight = weight, fontSize = 13.sp)
        Text(purpose, Modifier.weight(0.33f), color = color, fontWeight = weight, fontSize = 13.sp)
    }
}
