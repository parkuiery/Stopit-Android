package com.uiery.keep.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme

@Composable
internal fun OnboardingActionStack(
    primaryText: String,
    secondaryText: String,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    bottomSpacing: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            enabled = secondaryEnabled,
            onClick = onSecondaryClick,
        ) {
            Text(
                text = secondaryText,
                color = KeepTheme.colors.onSurfaceVariant,
            )
        }
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = primaryText,
            enabled = primaryEnabled,
            bottomSpacing = bottomSpacing,
            onClick = onPrimaryClick,
        )
    }
}
