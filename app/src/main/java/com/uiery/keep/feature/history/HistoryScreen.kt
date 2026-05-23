package com.uiery.keep.feature.history

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.analytics.AdPlacementMetadata
import com.uiery.keep.analytics.TrackedBannerAd
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.feature.history.component.HistoryItem
import org.orbitmvi.orbit.compose.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = null,
                            tint = KeepTheme.colors.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KeepTheme.colors.background,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                )
                Text(
                    text = stringResource(R.string.history_subtitle),
                    color = KeepTheme.colors.onTertiaryContainer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                HistoryItem(
                    id = R.drawable.ic_party,
                    title = stringResource(R.string.history_total_time),
                    time = formatMillisToMinSec(context,uiState.totalBlockTime)
                )
                HistoryItem(
                    id = R.drawable.ic_crown,
                    title = stringResource(R.string.history_longest_time),
                    time = formatMillisToMinSec(context,uiState.longBlockTime)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TrackedBannerAd(
                modifier = Modifier.fillMaxWidth(),
                metadata = AdPlacementMetadata(
                    screenName = KeepAnalyticsScreen.HISTORY,
                    screenContext = "summary",
                    placement = "history_bottom",
                    adUnitId = "ca-app-pub-1537867411423705/5324044368",
                ),
            )
        }
    }
}

private fun formatMillisToMinSec(context: Context, millis: Long): String {
    val totalSeconds = millis / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return context.getString(R.string.time_day_hour_min_sec, days, hours, minutes, seconds)
}
