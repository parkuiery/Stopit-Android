package com.uiery.keep.feature.lockhistory.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.lockhistory.PeriodType

@Composable
internal fun LockHistoryTab(
    modifier: Modifier = Modifier,
    selectedPeriod: PeriodType,
    onSelectPeriod: (PeriodType) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(KeepTheme.colors.tertiary),
    ) {
        TabItem(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.lock_history_week),
            isSelected = selectedPeriod == PeriodType.WEEK,
            onClick = { onSelectPeriod(PeriodType.WEEK) },
        )
        TabItem(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.lock_history_month),
            isSelected = selectedPeriod == PeriodType.MONTH,
            onClick = { onSelectPeriod(PeriodType.MONTH) },
        )
    }
}

@Composable
private fun TabItem(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) KeepTheme.colors.primary else KeepTheme.colors.tertiary
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) KeepTheme.colors.onPrimary else KeepTheme.colors.onTertiaryContainer,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
