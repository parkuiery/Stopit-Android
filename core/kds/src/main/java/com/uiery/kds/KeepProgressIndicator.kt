package com.uiery.kds

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

@Composable
fun KeepCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = KeepTheme.semanticColors.background.brandSolid,
    trackColor: Color = KeepTheme.semanticColors.background.brandWeak,
    strokeWidth: Dp = 4.dp,
    strokeCap: StrokeCap = StrokeCap.Butt,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        strokeWidth = strokeWidth,
        strokeCap = strokeCap,
    )
}

@Composable
fun KeepCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = KeepTheme.semanticColors.background.brandSolid,
    trackColor: Color = KeepTheme.semanticColors.background.brandWeak,
    strokeWidth: Dp = 4.dp,
    strokeCap: StrokeCap = StrokeCap.Butt,
) {
    CircularProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        strokeWidth = strokeWidth,
        strokeCap = strokeCap,
    )
}

@Composable
fun KeepLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = KeepTheme.semanticColors.background.brandSolid,
    trackColor: Color = KeepTheme.semanticColors.background.brandWeak,
) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

@Composable
fun KeepStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    require(totalSteps > 0) { "totalSteps must be greater than zero" }
    val safeCurrentStep = currentStep.coerceIn(0, totalSteps - 1)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = (safeCurrentStep + 1).toFloat(),
                    range = 1f..totalSteps.toFloat(),
                    steps = (totalSteps - 2).coerceAtLeast(0),
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isActive = index <= safeCurrentStep
            val width by animateFloatAsState(
                targetValue = if (index == safeCurrentStep) 24f else 8f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "KeepStepIndicatorWidth",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            KeepTheme.semanticColors.background.brandSolid
                        } else {
                            KeepTheme.semanticColors.background.brandWeak
                        },
                    ),
            )
        }
    }
}
