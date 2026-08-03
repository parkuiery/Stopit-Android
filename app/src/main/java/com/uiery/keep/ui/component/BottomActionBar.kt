package com.uiery.keep.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

/**
 * 화면 하단 액션 영역의 표면을 소유한다.
 *
 * SEED에서 `bg.disabled`와 `bg.layer-basement`는 light 모드에서 같은 `gray-200`이다. 화면 캔버스
 * 위에 컨트롤을 직접 올리면 비활성 상태가 배경과 대비 1.00:1이 되어 버튼이 사라진다. SEED는
 * 컨트롤이 `layer-default` 위에 놓인다고 전제하므로, 액션 영역은 그 표면으로 분리한다.
 *
 * 전폭을 채워야 배경과 구분되는 띠로 읽히므로 좌우 여백은 이 바가 직접 소유한다. 호출하는 화면은
 * 바깥 캔버스에 horizontal padding을 걸지 말고 스크롤 콘텐츠 쪽에 준다.
 *
 * 위 여백도 같은 이유로 바가 소유한다. 콘텐츠가 알아서 주도록 두면 CTA가 하나뿐인 화면에서 버튼이
 * 표면 경계에 그대로 붙는다. 아래 여백은 [com.uiery.kds.KeepButton]의 `bottomSpacing`이 이미 24dp를
 * 갖고 있으므로 여기서 더하지 않는다.
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
            .padding(horizontal = 24.dp)
            .padding(top = BOTTOM_ACTION_BAR_TOP_PADDING),
        content = content,
    )
}

internal val BOTTOM_ACTION_BAR_TOP_PADDING = 16.dp
