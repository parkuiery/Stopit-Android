package com.uiery.keep.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.uiery.kds.KeepCard
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepButtonSize
import com.uiery.kds.KeepButtonVariant
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion

/**
 * Shared card for repeat-block routine suggestions shown on Block, Home, and LockHistory surfaces.
 *
 * Screen owners provide surface-specific copy and optional action test tags while this component owns
 * the common hierarchy, spacing, CTA order, and dismiss affordance.
 */
@Composable
fun RepeatBlockRoutineSuggestionCard(
    modifier: Modifier = Modifier,
    suggestion: RepeatBlockRoutineSuggestion,
    @StringRes titleResId: Int,
    @StringRes messageResId: Int,
    onApplyClick: () -> Unit,
    onDismissClick: () -> Unit,
    applyActionTestTag: String? = null,
    dismissActionTestTag: String? = null,
) {
    KeepCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(titleResId),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = androidx.compose.ui.res.stringResource(
                    messageResId,
                    suggestion.prefillPackages.size,
                    suggestion.prefillStartTime,
                    suggestion.prefillEndTime,
                ),
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
                // 기본 줄바꿈은 폭이 차는 지점에서 끊어 마지막 한 마디만 둘째 줄에 남긴다.
                style = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph),
            )
            // 문구와 행동 사이는 문구끼리의 간격보다 넓어야 한 덩어리로 읽히지 않는다.
            Spacer(modifier = Modifier.height(4.dp))
            // SEED Action Button 조합 규칙을 따른다. 물러나는 뜻의 Neutral Weak 와 진행하는
            // 뜻의 Neutral Solid 를 함께 둘 때는 3:7 로 나눠 위계를 폭으로 드러낸다. 그래서
            // 좌·우 정렬을 고를 일이 없어지고, 두 버튼이 같은 크기 규격을 쓰므로 높이도
            // 저절로 맞는다.
            //
            // 가중치로 폭을 나누므로 글꼴을 키워도 버튼이 줄 밖으로 밀려나지 않는다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeepButton(
                    modifier = (dismissActionTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                        .weight(DISMISS_ACTION_WEIGHT),
                    text = androidx.compose.ui.res.stringResource(R.string.repeat_block_suggestion_dismiss_button),
                    variant = KeepButtonVariant.NeutralWeak,
                    size = KeepButtonSize.Medium,
                    bottomSpacing = false,
                    onClick = onDismissClick,
                )
                KeepButton(
                    modifier = (applyActionTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                        .weight(APPLY_ACTION_WEIGHT),
                    text = androidx.compose.ui.res.stringResource(R.string.repeat_block_suggestion_apply_button),
                    // 이 카드가 뜨는 화면에는 이미 브랜드색 주 행동이 있다. 카드의 행동은 그
                    // 자리를 뺏지 않도록 Brand Solid 가 아닌 Neutral Solid 를 쓴다.
                    variant = KeepButtonVariant.NeutralSolid,
                    size = KeepButtonSize.Medium,
                    bottomSpacing = false,
                    onClick = onApplyClick,
                )
            }
        }
    }
}

/** SEED 가 권하는 3:7. 물러나는 행동이 좁고, 진행하는 행동이 넓다. */
private const val DISMISS_ACTION_WEIGHT = 3f
private const val APPLY_ACTION_WEIGHT = 7f
