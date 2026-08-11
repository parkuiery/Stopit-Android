package com.uiery.kds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

@Composable
fun KeepSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.isNotEmpty()) { "items must not be empty" }
    val safeSelectedIndex = selectedIndex.coerceIn(items.indices)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(KeepTheme.semanticColors.background.neutralWeak)
            .selectableGroup()
            .padding(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == safeSelectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) {
                            KeepTheme.semanticColors.background.layerDefault
                        } else {
                            KeepTheme.semanticColors.background.transparent
                        },
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onItemSelected(index) },
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item,
                    color = if (selected) {
                        KeepTheme.semanticColors.foreground.neutral
                    } else {
                        KeepTheme.semanticColors.foreground.muted
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
