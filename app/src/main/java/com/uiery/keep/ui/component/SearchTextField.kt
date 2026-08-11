package com.uiery.keep.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.uiery.kds.KeepTextFieldVariant
import com.uiery.kds.KeepTextInput
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
    KeepTextInput(
        modifier = modifier,
        value = value(),
        onValueChange = onValueChange,
        placeholder = { Text(text = hint) },
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.outline_search_24),
                contentDescription = null,
            )
        },
        singleLine = singleLine,
        variant = KeepTextFieldVariant.Outline,
    )
}

@Preview
@Composable
private fun SearchTextFieldPreview() {
    KeepTheme {
        SearchTextField(
            value = { "ds" },
            hint = "검색",
            onValueChange = { },
        )
    }
}
