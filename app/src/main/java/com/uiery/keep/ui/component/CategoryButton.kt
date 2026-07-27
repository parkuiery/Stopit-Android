package com.uiery.keep.ui.component

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
fun CategoryButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
    categorySize: Int,
    websiteSize: Int = 0,
) {
    val moveIcon =
        if (enabled) R.drawable.round_arrow_forward_ios_24 else R.drawable.baseline_edit_off_24
    KeepCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        variant = KeepCardVariant.NeutralMuted,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                text = if (websiteSize > 0) {
                    stringResource(
                        id = R.string.lock_targets_selected,
                        categorySize,
                        websiteSize,
                    )
                } else {
                    stringResource(id = R.string.category_selected, categorySize)
                },
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(id = moveIcon),
                contentDescription = null,
                tint = if (enabled) {
                    KeepTheme.semanticColors.foreground.subtle
                } else {
                    KeepTheme.semanticColors.foreground.disabled
                },
            )
        }
    }
}
