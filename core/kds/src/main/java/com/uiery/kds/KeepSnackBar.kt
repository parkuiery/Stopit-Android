package com.uiery.kds

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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

/**
 * 여백은 화면이 아니라 여기서 갖는다. 호스트를 쓰는 화면마다 따로 주면 화면끼리 어긋나고,
 * 여백을 잊은 화면에서만 글자가 가장자리에 닿는다.
 */
@Composable
fun KeepSnackBar(
    modifier: Modifier = Modifier,
    snackbarData: SnackbarData,
) {
    Card(
        modifier = modifier
            // 떠 있는 면이므로 화면 가장자리에서 띄운다. 짧은 문구는 내용만큼만 커져서
            // 티가 나지 않지만, 한 줄에 담기지 못하는 문구는 이 여백이 없으면 폭을 끝까지
            // 쓰고 글자가 화면 양끝에 닿는다.
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // 넓은 화면에서 한 줄이 지나치게 길어지지 않게 한다.
            .widthIn(max = 480.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = KeepTheme.semanticColors.background.neutralInverted,
            contentColor = KeepTheme.semanticColors.foreground.inverted,
        )
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            text = snackbarData.visuals.message,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
    }
}
