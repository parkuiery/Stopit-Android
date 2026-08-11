package com.uiery.kds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

enum class KeepSwitchSize {
    Small,
    Medium,
    Large,
}

@Composable
fun KeepSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    size: KeepSwitchSize = KeepSwitchSize.Large,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val dimensions = keepSwitchDimensions(size)
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            KeepTheme.semanticColors.background.brandSolid
        } else {
            KeepTheme.semanticColors.foreground.placeholder
        },
        animationSpec = tween(
            durationMillis = 50,
            delayMillis = 20,
            easing = FastOutSlowInEasing,
        ),
        label = "KeepSwitchTrackColor",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) {
            dimensions.trackWidth - dimensions.padding - dimensions.thumbSize
        } else {
            dimensions.padding
        },
        animationSpec = tween(
            durationMillis = 150,
            easing = FastOutSlowInEasing,
        ),
        label = "KeepSwitchThumbOffset",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.8f,
        animationSpec = tween(
            durationMillis = 150,
            easing = FastOutSlowInEasing,
        ),
        label = "KeepSwitchThumbScale",
    )
    val interactionModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = ripple(
                bounded = false,
                radius = dimensions.touchTarget / 2,
            ),
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        }
    }

    Box(
        modifier = modifier
            .then(interactionModifier)
            .size(
                width = dimensions.containerWidth,
                height = dimensions.containerHeight,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = dimensions.trackWidth,
                    height = dimensions.trackHeight,
                )
                .alpha(if (enabled) 1f else DisabledOpacity)
                .clip(CircleShape)
                .background(trackColor),
        ) {
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .offset {
                        with(density) {
                            IntOffset(
                                x = thumbOffset.roundToPx(),
                                y = ((dimensions.trackHeight - dimensions.thumbSize) / 2)
                                    .roundToPx(),
                            )
                        }
                    }
                    .size(dimensions.thumbSize)
                    .scale(thumbScale)
                    .clip(CircleShape)
                    .background(KeepTheme.semanticColors.static.white),
                contentAlignment = Alignment.Center,
            ) {
                thumbContent?.invoke()
            }
        }
    }
}

internal data class KeepSwitchDimensions(
    val trackWidth: Dp,
    val trackHeight: Dp,
    val padding: Dp,
    val thumbSize: Dp,
    val touchTarget: Dp = 48.dp,
)

/**
 * 스위치를 감싸는 상자의 크기.
 *
 * `Modifier.size` 는 부모가 준 제약 안으로 맞춰지므로, 상자를 [KeepSwitchDimensions.touchTarget]
 * 으로만 잡으면 그보다 넓은 트랙(Large 는 52dp)이 48dp 로 눌린다. 그런데 켜짐 상태의 썸 위치는
 * 눌리기 전 트랙 폭으로 계산되어 썸이 오른쪽 끝에 붙고 여백이 사라진다.
 *
 * 접근성 최소 크기는 지키되 트랙을 누르지 않도록 둘 중 큰 값을 쓴다.
 */
internal val KeepSwitchDimensions.containerWidth: Dp get() = maxOf(touchTarget, trackWidth)

internal val KeepSwitchDimensions.containerHeight: Dp get() = maxOf(touchTarget, trackHeight)

internal fun keepSwitchDimensions(size: KeepSwitchSize): KeepSwitchDimensions = when (size) {
    KeepSwitchSize.Small -> KeepSwitchDimensions(
        trackWidth = 26.dp,
        trackHeight = 16.dp,
        padding = 2.dp,
        thumbSize = 12.dp,
    )

    KeepSwitchSize.Medium -> KeepSwitchDimensions(
        trackWidth = 38.dp,
        trackHeight = 24.dp,
        padding = 2.dp,
        thumbSize = 20.dp,
    )

    KeepSwitchSize.Large -> KeepSwitchDimensions(
        trackWidth = 52.dp,
        trackHeight = 32.dp,
        padding = 3.dp,
        thumbSize = 26.dp,
    )
}

private const val DisabledOpacity = 0.38f
