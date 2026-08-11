package com.uiery.keep.feature.lockhistory.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.uiery.kds.KeepSegmentedControl
import com.uiery.keep.R
import com.uiery.keep.feature.lockhistory.PeriodType

@Composable
internal fun LockHistoryTab(
    modifier: Modifier = Modifier,
    selectedPeriod: PeriodType,
    onSelectPeriod: (PeriodType) -> Unit,
) {
    val periods = listOf(PeriodType.WEEK, PeriodType.MONTH)
    KeepSegmentedControl(
        modifier = modifier,
        items = listOf(
            stringResource(R.string.lock_history_week),
            stringResource(R.string.lock_history_month),
        ),
        selectedIndex = periods.indexOf(selectedPeriod).coerceAtLeast(0),
        onItemSelected = { onSelectPeriod(periods[it]) },
    )
}
