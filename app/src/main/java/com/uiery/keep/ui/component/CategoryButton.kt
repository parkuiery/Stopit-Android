package com.uiery.keep.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
fun CategoryButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
    categorySize: Int,
) {
    val moveIcon =
        if (enabled) R.drawable.round_arrow_forward_ios_24 else R.drawable.baseline_edit_off_24
    val textColor = animateColorAsState(
        targetValue = if (enabled) KeepTheme.colors.surfaceVariant else KeepTheme.colors.onTertiaryContainer,
        label = ""
    ).value
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = onClick,
                enabled = enabled,
            )
            .background(shape = RoundedCornerShape(12.dp), color = KeepTheme.colors.tertiary)
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            modifier = Modifier.size(28.dp),
            painter = painterResource(id = R.drawable.shield),
            contentDescription = null,
        )
        Text(
            text = stringResource(id = R.string.category_selected, categorySize),
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(id = moveIcon),
            contentDescription = null,
            tint = KeepTheme.colors.onTertiaryContainer,
        )
    }
}