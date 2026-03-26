package com.uiery.keep.feature.lock.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CountDownContent(
    modifier: Modifier = Modifier,
    endTime: LocalDateTime,
) {
    val zone = ZoneId.systemDefault()
    val endEpochSeconds = endTime.atZone(zone).toEpochSecond()
    val nowEpochSeconds = System.currentTimeMillis() / 1000

    val endSeconds = (endEpochSeconds - nowEpochSeconds).toInt()
    var seconds by remember { mutableIntStateOf(endSeconds) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds--
        }
    }

    val days = remember(seconds) { seconds / 86400 }
    val formattedTime = remember(seconds) {
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60

        when {
            days > 0 || h > 0 -> String.format("%02d:%02d:%02d", h, m, s)
            else -> String.format("%02d:%02d", m, s)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (days > 0) {
            AnimatedContent(
                targetState = days,
                transitionSpec = {
                    slideInVertically { -it } + fadeIn() with slideOutVertically { it } + fadeOut()
                },
                label = "days"
            ) { d ->
                Text(
                    text = "${d}일 ",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
            }
        }
        formattedTime.forEach { c: Char ->
            AnimatedContent(
                targetState = c,
                transitionSpec = {
                    slideInVertically { -it } + fadeIn() with slideOutVertically { it } + fadeOut()
                },
                label = "timer"
            ) { time ->
                Text(
                    text = time.toString(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(id = R.string.remaining_time),
            fontWeight = FontWeight.SemiBold,
            color = KeepTheme.colors.onSurfaceVariant,
        )
    }
}