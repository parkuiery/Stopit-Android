package com.uiery.keep

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.KeepBannerAd
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme

@Composable
fun BlockScreen(
    modifier: Modifier = Modifier,
    packageName: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KeepTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        KeepBannerAd(
            modifier = Modifier.align(Alignment.TopCenter),
            adUnitId = "ca-app-pub-1537867411423705/5467753282"
        )
        Column {
            val appName = runCatching {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(applicationInfo).toString()
            }.getOrDefault("")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    modifier = Modifier
                        .sizeIn(
                            minHeight = 120.dp,
                            minWidth = 120.dp,
                        )
                        .clip(
                            RoundedCornerShape(12.dp)
                        ),
                    painter = painterResource(id = R.drawable.kepp_icon),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(id = R.string.block_screen_title),
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Center,
                    fontSize = 32.sp,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(top = 20.dp))
                Text(
                    text = stringResource(id = R.string.block_screen_message, appName),
                    textAlign = TextAlign.Center,
                    color = KeepTheme.colors.surfaceVariant,
                )
            }
            KeepButton(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp),
                text = stringResource(id = R.string.block_screen_close),
                onClick = onClose,
            )
        }
    }
}

@Preview
@Composable
private fun BlockScreenPreview() {
    BlockScreen(
        packageName = "",
        onClose = { },
    )
}