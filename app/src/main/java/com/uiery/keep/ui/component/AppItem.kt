package com.uiery.keep.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uiery.kds.KeepCheckbox
import com.uiery.keep.R
import com.uiery.kds.theme.KeepTheme

@Composable
fun AppItem(
    modifier: Modifier = Modifier,
    checkboxModifier: Modifier = Modifier,
    image: ImageBitmap,
    name: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeepCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = checkboxModifier,
        )
        Image(
            modifier = Modifier.size(30.dp),
            bitmap = image,
            contentDescription = null,
        )
        Text(
            text = name,
            color = KeepTheme.colors.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun AppItemPreview() {
    KeepTheme(darkTheme = true) {
        AppItem(
            image = ImageBitmap.imageResource(id = R.drawable.kepp_icon),
            name = "dasdasa",
            checked = false,
            onCheckedChange = { },
        )
    }
}