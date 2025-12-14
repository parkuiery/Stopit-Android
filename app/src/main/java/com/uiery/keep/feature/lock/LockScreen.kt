package com.uiery.keep.feature.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uiery.kds.KeepBannerAd
import com.uiery.kds.RotatingCircleGradient
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.component.CategoryButton
import com.uiery.keep.feature.lock.component.CountDownContent
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun LockScreen(
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit,
) {
    val uiState by viewModel.collectAsState()
    val configuration = LocalConfiguration.current

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is LockSideEffect.MoveToHome -> onNavigateHome()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            CategoryButton(
                modifier = Modifier.padding(top = 60.dp),
                onClick = { },
                enabled = false,
                categorySize = uiState.selectedAppPackage.size,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    Image(
                        modifier = Modifier
                            .sizeIn(
                                minHeight = 100.dp,
                                minWidth = 100.dp,
                            )
                            .clip(RoundedCornerShape(12.dp)),
                        painter = painterResource(id = R.drawable.kepp_icon),
                        contentDescription = null,
                    )
                    if (uiState.isRoutine) {
                        val name = uiState.routines.firstOrNull()?.name.orEmpty()
                        Text(
                            text = stringResource(
                                id = R.string.lock_screen_routine_running,
                                name,
                            ),
                            fontWeight = FontWeight.Medium,
                            color = KeepTheme.colors.surfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        CountDownContent(
                            endTime = uiState.lockTime
                        )
                    }
                }
                RotatingCircleGradient(
                    size = configuration.screenWidthDp.dp - 80.dp,
                )
            }
            Column(
                modifier = modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val lockTime = uiState.lockTime
                Text(
                    text = stringResource(id = R.string.keep_on_status),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        id = R.string.lock_screen_unavailable_message,
                        lockTime.hour,
                        lockTime.minute
                    ),
                    color = KeepTheme.colors.surfaceVariant,
                )
                KeepBannerAd(
                    modifier = Modifier.padding(top = 20.dp),
                    adUnitId = "ca-app-pub-1537867411423705/7892727021"
                )
            }
        }
    }
}