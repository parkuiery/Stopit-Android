package com.uiery.keep.feature.onboarding

import androidx.compose.foundation.background
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
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme

/**
 * Onboarding 하단 액션 영역의 표면을 소유한다.
 *
 * SEED에서 `bg.disabled`와 `bg.layer-basement`는 light 모드에서 같은 `gray-200`이다. 화면 캔버스
 * 위에 컨트롤을 직접 올리면 disabled 상태가 배경과 완전히 같은 색이 되어 사라지므로, 액션 영역은
 * `layerDefault`로 분리한다.
 */
@Composable
internal fun OnboardingBottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KeepTheme.semanticColors.background.layerDefault)
            .padding(horizontal = 24.dp),
        content = content,
    )
}

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
    OnboardingBottomActionBar {
        KeepTextButton(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
            bottomSpacing = bottomSpacing,
            onClick = onPrimaryClick,
        )
    }
}
