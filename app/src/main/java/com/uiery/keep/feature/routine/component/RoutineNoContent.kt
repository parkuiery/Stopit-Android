package com.uiery.keep.feature.routine.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
fun RoutineNoContent(
    modifier: Modifier = Modifier,
    onAddRoutine: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.routine_no_content_message),
            color = KeepTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        KeepButton(
            text = stringResource(R.string.routine_add_button),
            onClick = onAddRoutine,
        )
    }
}