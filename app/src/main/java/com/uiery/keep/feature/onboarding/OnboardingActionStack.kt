package com.uiery.keep.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import com.uiery.kds.KeepTextButton
import com.uiery.kds.KeepTextButtonVariant
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uiery.keep.ui.component.BottomActionBar
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme

/** Onboarding에서 쓰는 공용 하단 액션 바. 표면 규칙은 [BottomActionBar]가 소유한다. */
@Composable
internal fun OnboardingBottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = BottomActionBar(modifier = modifier, content = content)

@Composable
internal fun OnboardingActionStack(
    primaryText: String,
    secondaryText: String,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
) {
    OnboardingBottomActionBar {
        KeepTextButton(
            // 위아래 여백은 액션 바가 소유한다. 여기서는 primary CTA와의 간격만 책임진다.
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            enabled = secondaryEnabled,
            variant = KeepTextButtonVariant.Muted,
            onClick = onSecondaryClick,
        ) {
            Text(text = secondaryText)
        }
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = primaryText,
            enabled = primaryEnabled,
            bottomSpacing = false,
            onClick = onPrimaryClick,
        )
    }
}
