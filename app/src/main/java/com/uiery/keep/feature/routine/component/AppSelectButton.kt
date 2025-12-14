package com.uiery.keep.feature.routine.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
internal fun AppSelectButton(
    modifier: Modifier = Modifier,
    selectApps: Set<String>,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable{ onClick() }
            .background(
                color = KeepTheme.colors.tertiary,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (image, tile) = if(selectApps.isEmpty()) R.drawable.ic_question_face to stringResource(R.string.select_apps_to_lock) else R.drawable.shield to stringResource(R.string.category_selected,selectApps.size)
        Image(
            modifier = Modifier.size(16.dp),
            painter = painterResource(image),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = tile,
            color = KeepTheme.colors.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.round_arrow_forward_ios_24),
            contentDescription = null,
            tint = KeepTheme.colors.onTertiaryContainer,
        )
    }
}