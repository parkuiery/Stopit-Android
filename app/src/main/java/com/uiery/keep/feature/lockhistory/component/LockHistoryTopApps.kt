package com.uiery.keep.feature.lockhistory.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.uiery.kds.theme.KeepTheme
import com.uiery.kds.KeepCard
import com.uiery.keep.R
import com.uiery.keep.feature.lockhistory.LockHistoryPerformanceReportReadModel
import com.uiery.keep.util.AppDisplayMetadata
import com.uiery.keep.util.rememberAppDisplayMetadataResolver

@Composable
internal fun LockHistoryTopApps(
    modifier: Modifier = Modifier,
    topApps: List<Pair<String, Int>>,
    report: LockHistoryPerformanceReportReadModel,
    onClick: () -> Unit,
) {
    if (topApps.isEmpty()) return
    val appDisplayMetadataResolver = rememberAppDisplayMetadataResolver()
    val appMetadata = remember(topApps, appDisplayMetadataResolver) {
        topApps.map { (packageName, blockCount) ->
            appDisplayMetadataResolver.resolve(packageName) to blockCount
        }
    }
    val titleText = stringResource(report.topAppsTitleResId)
    val supportingText = stringResource(report.topAppsSupportingResId)
    val accessibilityDescription = buildList {
        add(titleText)
        add(supportingText)
        appMetadata.forEachIndexed { index, (metadata, blockCount) ->
            add("#${index + 1}")
            add(metadata.contentDescription)
            add(stringResource(R.string.lock_history_block_count, blockCount))
        }
    }.joinToString(separator = ". ")

    KeepCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Text(
            text = titleText,
            color = KeepTheme.semanticColors.foreground.neutral,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = supportingText,
            color = KeepTheme.semanticColors.foreground.muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            appMetadata.forEachIndexed { index, (metadata, blockCount) ->
                TopAppItem(
                    modifier = Modifier.weight(1f),
                    rank = index + 1,
                    appMetadata = metadata,
                    blockCount = blockCount,
                )
            }
        }
        }
    }
}

@Composable
private fun TopAppItem(
    modifier: Modifier = Modifier,
    rank: Int,
    appMetadata: AppDisplayMetadata,
    blockCount: Int,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "#$rank",
            color = KeepTheme.semanticColors.foreground.brand,
            style = MaterialTheme.typography.labelSmall,
        )
        Image(
            bitmap = appMetadata.icon.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Text(
            text = appMetadata.label,
            color = KeepTheme.semanticColors.foreground.neutral,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.lock_history_block_count, blockCount),
            color = KeepTheme.semanticColors.foreground.muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
