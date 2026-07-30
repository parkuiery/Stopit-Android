package com.uiery.kds

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme

@Composable
fun KeepSnackBar(
    modifier: Modifier = Modifier,
    snackbarData: SnackbarData,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = KeepTheme.semanticColors.background.neutralInverted,
            contentColor = KeepTheme.semanticColors.foreground.inverted,
        )
    ) {
        Text(
            modifier = Modifier.padding(10.dp),
            text = snackbarData.visuals.message,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
    }
}
