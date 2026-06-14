package com.uiery.keep.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uiery.kds.KeepCheckbox
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
fun AppItem(
    modifier: Modifier = Modifier,
    checkboxModifier: Modifier = Modifier,
    image: ImageBitmap,
    name: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
) {
    val selectionStateDescription = stringResource(
        id = if (checked) R.string.cd_tab_selected else R.string.cd_tab_not_selected,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .semantics { stateDescription = selectionStateDescription }
            .then(modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeepCheckbox(
            checked = checked,
            onCheckedChange = null,
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