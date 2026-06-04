package com.uiery.keep.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
fun SearchTextField(
    modifier: Modifier = Modifier,
    value: () -> String,
    hint: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
) {
    val hintAlpha by animateFloatAsState(
        targetValue = if (value().isEmpty()) {
            1f
        } else {
            0f
        },
        label = "",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = KeepTheme.colors.tertiary,
    ) {
        BasicTextField(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            value = value(),
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = KeepTheme.colors.onSurfaceVariant,
            )
        ) { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_search_24),
                    contentDescription = null,
                    tint = KeepTheme.colors.onSurface,
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    innerTextField()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.alpha(hintAlpha),
                            text = hint,
                            fontSize = 16.sp,
                            color = KeepTheme.colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SearchTextFieldPreview() {
    SearchTextField(
        value = { "ds" },
        hint = "검색",
        onValueChange = { },
    )
}