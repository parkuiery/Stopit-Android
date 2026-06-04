package com.uiery.keep.feature.menu.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
fun MenuItem(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = KeepTheme.colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            modifier = Modifier.size(18.dp),
            painter = painterResource(R.drawable.round_arrow_forward_ios_24),
            contentDescription = null,
            tint = KeepTheme.colors.onTertiaryContainer,
        )
    }
}

@Preview
@Composable
private fun MenuItemPreview() {
    MenuItem(
        icon = R.drawable.laptop,
        title = "dasdasad",
        onClick =  {},
    )
}