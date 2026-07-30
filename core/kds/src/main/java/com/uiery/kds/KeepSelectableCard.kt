package com.uiery.kds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

@Composable
fun KeepSelectableCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    supportingContent: @Composable (() -> Unit)? = null,
) {
    KeepCard(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            ),
        variant = if (selected) KeepCardVariant.BrandWeak else KeepCardVariant.LayerDefault,
        bordered = true,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeepRadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = if (enabled) {
                        KeepTheme.semanticColors.foreground.neutral
                    } else {
                        KeepTheme.semanticColors.foreground.disabled
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = description,
                    color = if (enabled) {
                        KeepTheme.semanticColors.foreground.muted
                    } else {
                        KeepTheme.semanticColors.foreground.disabled
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (supportingContent != null) {
                    Box(
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        supportingContent()
                    }
                }
            }
        }
    }
}
