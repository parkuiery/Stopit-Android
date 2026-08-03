package com.uiery.keep.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

/**
 * 화면 하단 액션 영역의 표면을 소유한다.
 *
 * SEED에서 `bg.disabled`와 `bg.layer-basement`는 light 모드에서 같은 `gray-200`이다. 화면 캔버스
 * 위에 컨트롤을 직접 올리면 비활성 상태가 배경과 대비 1.00:1이 되어 버튼이 사라진다. SEED는
 * 컨트롤이 `layer-default` 위에 놓인다고 전제하므로, 액션 영역은 그 표면으로 분리한다.
 *
 * 이 바는 자기 여백을 전부 소유한다. 여백을 콘텐츠에 맡기면 CTA가 하나뿐인 화면에서 버튼이 표면
 * 경계에 그대로 붙는다. 따라서 안에 놓는 [com.uiery.kds.KeepButton]은 `bottomSpacing = false`로
 * 두어야 아래 여백이 두 번 들어가지 않는다.
 *
 * 시스템 내비게이션 바 인셋도 여기서 먹는다. [background]를 [navigationBarsPadding]보다 먼저 걸어
 * 표면이 제스처 바 뒤까지 칠해지게 한다. 그래야 흰 띠와 내비게이션 바가 한 덩어리로 읽힌다. 이
 * 바를 쓰는 화면은 컨테이너에서 하단 인셋을 소비하면 안 된다 — [withoutBottomInset]을 쓴다.
 */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KeepTheme.semanticColors.background.layerDefault)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        content = content,
    )
}

/**
 * 하단 인셋을 뺀 [PaddingValues]를 돌려준다.
 *
 * Scaffold가 준 padding을 그대로 쓰면 내비게이션 바 자리가 컨테이너 여백으로 잡혀 [BottomActionBar]
 * 표면이 그 위에서 끊긴다. 하단 인셋은 바가 직접 처리하므로 컨테이너는 나머지만 소비한다.
 * 액션 바가 없는 분기에서는 이 함수를 쓰지 말고 원래 padding을 그대로 넘겨야 한다.
 */
@Composable
fun PaddingValues.withoutBottomInset(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = 0.dp,
    )
}
