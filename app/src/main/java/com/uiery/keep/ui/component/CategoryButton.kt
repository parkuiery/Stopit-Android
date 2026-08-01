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
) {
    val moveIcon =
        if (enabled) R.drawable.round_arrow_forward_ios_24 else R.drawable.baseline_edit_off_24
    KeepCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        // 잠금 중에도 무엇을 차단하고 있는지는 계속 읽혀야 한다. disabled 로 두면 면이
        // background.disabled(gray200)가 되어 화면 캔버스와 같은 색이 되고 카드가 사라진다.
        // 조작 불가는 edit-off 아이콘이 전달한다.
        readOnly = !enabled,
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
                text = stringResource(id = R.string.category_selected, categorySize),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(id = moveIcon),
                contentDescription = null,
                tint = if (enabled) {
                    KeepTheme.semanticColors.foreground.subtle
                } else {
                    KeepTheme.semanticColors.foreground.muted
                },
            )
        }
    }
}
