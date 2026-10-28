package tech.zakomi.zakdroid.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.zakomi.zakdroid.core.R

/** The zakomi TECH logo: white interlocking Z in a navy rounded square. */
@Composable
fun ZakomiLogo(modifier: Modifier = Modifier, size: Dp = 48.dp, contentDescription: String? = "zakomi TECH") {
    Image(
        painter = painterResource(R.drawable.zakomi_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
