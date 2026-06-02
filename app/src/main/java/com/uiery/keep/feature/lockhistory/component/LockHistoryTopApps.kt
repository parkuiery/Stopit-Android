package com.uiery.keep.feature.lockhistory.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.util.AppDisplayMetadataResolver

@Composable
internal fun LockHistoryTopApps(
    modifier: Modifier = Modifier,
    topApps: List<Pair<String, Int>>,
    onClick: () -> Unit,
) {
    if (topApps.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.colors.tertiary)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.lock_history_top_apps_title),
            color = KeepTheme.colors.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            topApps.forEachIndexed { index, (packageName, blockCount) ->
                TopAppItem(
                    modifier = Modifier.weight(1f),
                    rank = index + 1,
                    packageName = packageName,
                    blockCount = blockCount,
                )
            }
        }
    }
}

@Composable
private fun TopAppItem(
    modifier: Modifier = Modifier,
    rank: Int,
    packageName: String,
    blockCount: Int,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val appDisplayMetadataResolver = remember(packageManager) {
        AppDisplayMetadataResolver(packageManager)
    }

    val appMetadata = remember(packageName, appDisplayMetadataResolver) {
        appDisplayMetadataResolver.resolve(packageName)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "#$rank",
            color = KeepTheme.colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        appMetadata.icon?.let { icon ->
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Text(
            text = appMetadata.label,
            color = KeepTheme.colors.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.lock_history_block_count, blockCount),
            color = KeepTheme.colors.onTertiaryContainer,
            fontSize = 10.sp,
        )
    }
}
