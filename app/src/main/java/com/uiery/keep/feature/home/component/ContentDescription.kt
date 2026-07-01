package com.uiery.keep.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
fun ContentDescription(
    modifier: Modifier = Modifier,
    isKeep: Boolean,
    startTime: Long,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val description = if(isKeep) R.string.keep_on_message else R.string.keep_off_message
        if(isKeep) {
            TimerContent(startTime = startTime)
        } else {
            Text(
                text = stringResource(R.string.keep_off_status),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = KeepTheme.colors.error,
            )
        }
        Text(
            text = stringResource(description),
            color = KeepTheme.colors.onSurface,
        )
    }
}