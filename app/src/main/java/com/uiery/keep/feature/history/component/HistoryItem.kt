package com.uiery.keep.feature.history.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme

@Composable
internal fun HistoryItem(
    modifier: Modifier = Modifier,
    @DrawableRes id: Int,
    title: String,
    time: String,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = KeepTheme.colors.onTertiary, shape = RoundedCornerShape(20.dp))
            .padding(vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = KeepTheme.colors.surface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = time,
            color = KeepTheme.colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}